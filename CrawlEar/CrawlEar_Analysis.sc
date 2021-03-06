CrawlEar_Analysis {
	classvar analysisData;
	classvar sigmaData;
	classvar archiveName = "analysis_data";

	const <index_names = 0, <index_fftUse = 1, <index_logUse = 2, <index_funcs = 3, <index_threshes = 4;

	// [name, uses_fft, analysis function, [thresholds (2, 2.5, 3, 3.5, 4 sigma)]]
	*analyses {
		// try to update the analysis data if the array is nil or if no analysis data has yet been loaded
		if(analysisData.isNil || (analysisData!?{|x| x.first[index_threshes].isNil}?true)) {
			var fileData, hardData;

			// hardcoded data
			hardData = [
				[\p25, true, true, {arg chain; SpecPcile.kr(chain, 0.25, 1).log2}],
				[\p50, true, true, {arg chain; SpecPcile.kr(chain, 0.50, 1).log2}],
				[\p75, true, true, {arg chain; SpecPcile.kr(chain, 0.75, 1).log2}],
				[\p90, true, true, {arg chain; SpecPcile.kr(chain, 0.90, 1).log2}],
				[\flat, true, false, {arg chain; SpecFlatness.kr(chain)}],
				[\cent, true, true, {arg chain; SpecCentroid.kr(chain).log2}],
				[\amp1, false, false, {arg sig; Amplitude.kr(sig, 0.01, 0.1)}],
				[\amp2, false, false, {arg sig; Amplitude.kr(sig, 0.25, 0.3)}]
			];

			// load in analysis data if a file exists
			if(File.exists(this.pr_dataFilename)) {
				var fileData = Object.readArchive(this.pr_dataFilename);
				hardData = hardData.collect {
					|arr, i|
					arr.add(fileData[i]);
				};
			} {
				"no analysis data file".warn;
			};

			analysisData = hardData;
		};
		^analysisData;
	}

	// updates the threshold information after new data has been written
	*updateAnalysisThreshes {
		// just call analyses if there's no data loaded yet
		if(analysisData.isNil) {^this.analyses};

		if(File.exists(this.pr_dataFilename)) {
			var fileData = Object.readArchive(this.pr_dataFilename);
			analysisData = analysisData.collect {
				|arr, i|
				if(arr[index_threshes].isNil) {
					arr.add(fileData[i]); // add in new data if there's no data
				} {
					arr[arr.size-1] = fileData[i]; // otherwise, replace it
					arr;
				}
			};
		} {
			"no analysis data file".warn;
		};
		^analysisData;
	}

	// gives the names of the particular analysis components
	*analyses_names {
		^this.analyses.flop[index_names];
	}

	// true if the analysis method uses an FFT chain as input
	*analyses_fftUse {
		^this.analyses.flop[index_fftUse];
	}

	// true if the analysis has an output that uses a logarithmic scale and is therefore prone to NaN outputs
	*analyses_logUse {
		^this.analyses.flop[index_logUse];
	}

	// provides the actual functions used for analysis, which are SynthDef.wrap'd
	*analyses_funcs {
		^this.analyses.flop[index_funcs];
	}

	// provides the entire set of threshold data
	*analyses_allThreshes {
		^this.analyses.flop[index_threshes];
	}

	// provides a set of threshold data for a specific sigma value. only certain values (2 to 4.5 by increments of 0.5) are defined; other values are interpolated linearly
	*analyses_threshes {
		arg sigma;
		sigma = sigma - 2 * 2;
		if(sigma < 0 || sigma > this.analyses.first[index_threshes].size) {
			this.invalidInput(thisMethod, "sigma", sigma, "Between 0 and % inclusive.".format(this.analyses.first[index_threshes].size));
		};

		^this.analyses_allThreshes.flop.blendAt(sigma);
	}

	*inputDir {
		^"test_audio/input".resolveRelative;
	}

	*outputDir {
		^"test_audio/output".resolveRelative;
	}

	*pr_dataFilename {
		^archiveName.resolveRelative;
	}

	// probability that a random variable with gaussian distribution lies in the range of +/- 2, 2.5, 3, 3.5, 4, 4.5 sigma
	*sigmas {
		sigmaData = sigmaData ? [0.954499736,0.987580669,0.997300204,0.999534742,0.999936658,0.999993204];
		^sigmaData;
	}

	// run an analysis on all files in the inputDir
	*performAnalysis {
		var server = Server.local;
		var files = PathName(this.inputDir).files;
		var completionConditions = Array.fill(files.size, Condition(false));
		var synthName = 'analyzeBuffer';

		fork {
			// setup server
			server.options.sampleRate_(Crawlspace.sr);
			server.options.blockSize_(CrawlEar.blocksize);
			server.bootSync(Condition());

			// setup analysis synthdef
			SynthDef(synthName, {
				arg inbuf, outbuf;
				var sig = PlayBuf.ar(2, inbuf, BufRateScale.ir(inbuf), doneAction:2);
				var chain = FFT(LocalBuf(Crawlspace.fftsize), BHiPass.ar(sig, CrawlEar.hpf));
				var stats;

				stats = this.analyses.collect({
					|entry|
					SynthDef.wrap(entry[index_funcs], entry[index_fftUse].if(\kr, \ar), entry[index_fftUse].if(chain, sig));
				});
				stats = K2A.ar(stats);
				DiskOut.ar(outbuf,stats);
			}).add;
			server.sync(Condition());

			// process each file
			files.do {
				|filepath,i|
				var inbuf, outbuf, outputFilename, id;

				// set up buffers
				inbuf = Buffer.read(server, filepath.fullPath);
				outbuf = Buffer.alloc(server,server.sampleRate.nextPowerOfTwo,this.analyses.size);
				outputFilename = this.outputDir +/+ filepath.fileNameWithoutExtension ++ "_analysis.wav";
				outbuf.write(outputFilename, "wave", "float", leaveOpen:true);
				server.sync(Condition());

				// create synth
				this.outln(format("file % (%): % seconds", filepath.fileName, i, inbuf.duration.round(0.01)));
				id = Synth(synthName, [\inbuf, inbuf.bufnum, \outbuf, outbuf.bufnum]).nodeID;

				// listen for completion and mark condition true when done
				OSCFunc({
					outbuf.close;
					outbuf.free;
					completionConditions[i].test_(true);
					this.outln("Done: %".format(outputFilename));
				}, path:'/n_end', argTemplate:[id]).oneShot;
			}
		}

		^completionConditions;
	}

	// helper method for calculateSigmaThresholds. gathers the necessary data from the files in outputDir after running performAnalysis
	*pr_collectOutputData {
		var files, allData;

		files = PathName(this.outputDir).files;
		allData = [];

		files.do {
			|filepath, index|
			var sf, frame, numframes, channel_data, hopsize;
			var percentage_increment, next_percentage;
			next_percentage = percentage_increment = 10;

			sf = SoundFile.openRead(filepath.fullPath);
			frame = FloatArray.newClear(sf.numChannels);
			hopsize = Crawlspace.fftsize.div(2);
			numframes = sf.numFrames.div(hopsize);
			channel_data = Array.fill(this.analyses.size, {FloatArray.newClear(numframes)});

			numframes.do {
				|i|

				sf.readData(frame);
				// if(frame.any(_.isNaN) || frame[0] == 0) {
				// this.outln("ignoring bad frame: file %, index %".format(index, i));
				// } {
				for(0, channel_data.size-1) {
					|j|
					channel_data[j][i] = frame[j];
				};

				if((i/numframes*100) >= next_percentage) {
					next_percentage = next_percentage + percentage_increment;
					this.outln(format("progress: file %, %\\%", index, (i/numframes*100).round(0.1)));
				};
				sf.seek(hopsize-1, 1);
			};

			this.outln(format("progress: file %, %\\%", index, 100));

			sf.close;
			channel_data = this.pr_filterBadData(channel_data);
			allData = allData.add(channel_data);
		};

		^allData.flop;
	}

	// helper method for calculateSigmaThresholds
	*pr_smooth {
		arg arr;
		var res, sum, n;

		n = CrawlEar.smoother_width;
		res = Array.newClear(arr.size-n+1);
		sum = arr[0..(n-1)].sum;
		if(arr.size-n-1 < 0) {
			^[]
		};
		for(0, arr.size-n-1) {
			|i|
			res[i] = sum/n;
			sum = sum - arr[i] + arr[i+n];
		};
		res[arr.size-n] = sum/n;

		^res;
	}

	// helper method for calculateSigmaThresholds
	// gets rid of bad data frames that are not useful for analysis but produced normally by the analysis functions. usually caused by a completely silent input signal
	*pr_filterBadData {
		arg channel_data;
		var frames = channel_data.first.size;
		var i = 0, j = frames-1; // if any channel has a 0 and uses log, or any channel has NaN, keep counting
		var bBadVals = true;

		while {bBadVals && (i < frames)} {
			bBadVals = this.analyses_logUse.any {
				|bLog, chan|
				(bLog && (channel_data[chan][i] == 0)) || channel_data[chan][i].isNaN;
			};
			if(bBadVals) {
				i = i + 1;
			};
		};

		bBadVals = true;
		while {bBadVals && (j > i)} {
			bBadVals = this.analyses_logUse.any {
				|bLog, chan|
				(bLog && (channel_data[chan][i] == 0)) || channel_data[chan][i].isNaN;
			};
			if(bBadVals) {
				j = j - 1;
			};
		};

		this.outln("dropping % from start, % from end".format(i+1, j+1-frames));
		^channel_data.collect({|chan| chan.drop(i+1).drop(j+1-frames)});
	}

	// calculate the derivative thresholds used by CrawlEar to splice live input
	*calculateSigmaThresholds {
		var allData = this.pr_collectOutputData();
		var thresholdData;

		allData = allData.collect({
			|arr,i|
			arr = arr.reduce('++');
			arr = this.pr_smooth(arr);
			arr = arr.differentiate.drop(1);
			arr = arr.abs.sort;
			this.outln("processed: %/%".format(i+1,allData.size));
			arr;
		});

		thresholdData = allData.collect({
			|arr,i|
			arr[arr.size * this.sigmas];
		});

		^thresholdData;
	}

	// calculate sigma thresholds and write them to an archive file to be loaded later. avoids directly handling data
	*writeSigmaThresholds {
		var data = this.calculateSigmaThresholds();
		data.writeArchive(this.pr_dataFilename);
		this.outln("Archive written.");
	}

	*fullAnalysis {
		fork {
			var conditions;
			this.outln("----CrawlEar_Analysis.fullAnalysis----\n");
			this.outln("\tSTARTING ANALYSIS");
			this.outln("\t-----------------\n");
			conditions = this.performAnalysis;

			this.outln("");
			this.outln("\tWAITING TO COMPLETE");
			this.outln("\t-------------------\n");
			while {conditions.every(_.test).not} {1.wait};

			this.outln("");
			this.outln("\tCALCULATING THRESHOLDS");
			this.outln("\t----------------------\n");
			this.writeSigmaThresholds();

			this.outln("----------------Done------------------");
		}
	}

	*outln {
		arg o;
		postln(o);
	}
}
