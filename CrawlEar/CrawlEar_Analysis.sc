CrawlEar_Analysis {
	classvar analysis_data;
	classvar sigma_data;
	classvar archiveName = "analysis_data";

	const index_names = 0, index_fftUse = 1, index_logUse = 2, index_funcs = 3, index_threshes = 4;

	// [name, uses_fft, analysis function, [thresholds (2, 2.5, 3, 3.5, 4 sigma)]]
	*analyses {
		if(analysis_data.isNil || (analysis_data!?{|x| x.first[index_threshes].isNil}?true)) {
			var fileData, hardData;
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
			if(File.exists(this.pr_dataFilename)) {
				fileData = Object.readArchive(this.pr_dataFilename);
				hardData = hardData.collect {
					|arr, i|
					arr.add(fileData[i]);
				};
			};
			analysis_data = hardData;
		};
		^analysis_data;
	}

	*analyses_names {
		^this.analyses.flop[index_names];
	}

	*analyses_fftUse {
		^this.analyses.flop[index_fftUse];
	}

	*analyses_logUse {
		^this.analyses.flop[index_logUse];
	}

	*analyses_funcs {
		^this.analyses.flop[index_funcs];
	}

	*analyses_allThreshes {
		^this.analyses.flop[index_threshes];
	}

	*analyses_threshes {
		arg sigma;
		sigma = sigma - 2 * 2;
		if(sigma < 0 || sigma > this.analyses.first[index_threshes].size) {
			this.invalidInput(thisMethod, "sigma", sigma, "Between 0 and % inclusive.".format(this.analyses.first[index_threshes].size));
		};

		^this.analyses.flop[index_threshes].flop.blendAt(sigma);
	}

	*input_dir {
		^"test_audio/input".resolveRelative;
	}

	*output_dir {
		^"test_audio/output".resolveRelative;
	}

	// 2, 2.5, 3, 3.5, 4, 4.5
	*sigmas {
		sigma_data = sigma_data ? [0.954499736,0.987580669,0.997300204,0.999534742,0.999936658,0.999993204];
		^sigma_data;
	}

	*performAnalysis {
		var server = Server.local;
		server.options.sampleRate_(Crawlspace.sr);
		server.options.blockSize_(CrawlEar.blocksize);
		fork {
			var dur, files, analyses;
			server.bootSync(Condition());

			files = PathName(this.input_dir).files;
			analyses = CrawlEar_Analysis.analyses;

			SynthDef(\analyze_buffer, {
				arg inbuf, outbuf;
				var sig = PlayBuf.ar(2, inbuf, BufRateScale.ir(inbuf), doneAction:2);
				var chain = FFT(LocalBuf(Crawlspace.fftsize), BHiPass.ar(sig, CrawlEar.hpf));
				var stats;

				stats = analyses.collect({
					|entry|
					var func = entry[index_funcs];
					SynthDef.wrap(func, entry[index_fftUse].if(\kr, \ar), entry[index_fftUse].if(chain, sig));
				});
				// stats = stats * BinaryOpUGen('==', CheckBadValues.kr(stats, 0, 0), 0);
				stats = K2A.ar(stats);
				DiskOut.ar(outbuf,stats);
			}).add;
			server.sync(Condition());

			files.do {
				|filepath,i|
				var inbuf, outbuf, output_filename, id;

				inbuf = Buffer.read(server, filepath.fullPath);
				outbuf = Buffer.alloc(server,server.sampleRate.nextPowerOfTwo,analyses.size);
				output_filename = this.output_dir +/+ filepath.fileNameWithoutExtension ++ "_analysis.wav";
				outbuf.write(output_filename, "wave", "float", leaveOpen:true);
				server.sync(Condition());
				dur = inbuf.duration;
				format("file % (%): % seconds", filepath.fileName, i, dur.round(0.01)).postln;
				id = Synth(\analyze_buffer, [\inbuf, inbuf.bufnum, \outbuf, outbuf.bufnum]).nodeID;

				OSCFunc({
					outbuf.close;
					outbuf.free;
					postln("Done: %".format(output_filename));
				}, path:'/n_end', argTemplate:[id]).oneShot;
			}
		}
	}

	*pr_collectOutputData {
		var files, all_data;

		files = PathName(this.output_dir).files;
		all_data = [];

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
				// postln("ignoring bad frame: file %, index %".format(index, i));
				// } {
				for(0, channel_data.size-1) {
					|j|
					channel_data[j][i] = frame[j];
				};

				if((i/numframes*100) >= next_percentage) {
					next_percentage = next_percentage + percentage_increment;
					postln(format("progress: file %, %\\%", index, (i/numframes*100).round(0.1)));
				};
				sf.seek(hopsize-1, 1);
			};

			postln(format("progress: file %, %\\%", index, 100));

			sf.close;
			channel_data = this.pr_filterBadData(channel_data);
			all_data = all_data.add(channel_data);
		};

		^all_data.flop;
	}

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

	*pr_filterBadData {
		arg channel_data;
		var frames = channel_data.first.size;
		var i = 0, j = frames-1; // if any channel has a 0 and uses log, or any channel has NaN, keep counting
		var log0 = true, nan = true;

		while {(log0 || nan) && (i < channel_data.first.size)} {
			log0 = false;
			nan = false;
			this.analyses_logUse.do {
				|bLog, chan|
				log0 = log0 || (bLog && channel_data[chan][i] == 0);
			};
			this.analyses.size.do {
				|chan|
				nan = nan || channel_data[chan][i].isNaN;
			};
			if(log0 || nan) {
				i = i + 1;
			};
		};

		while {(log0 || nan) && (j > i)} {
			log0 = false;
			nan = false;
			this.analyses_logUse.do {
				|bLog, chan|
				log0 = log0 || (bLog && channel_data[chan][j] == 0);
			};
			this.analyses.size.do {
				|chan|
				nan = nan || channel_data[chan][j].isNaN;
			};
			if(log0 || nan) {
				j = j - 1;
			};
		};

		"dropping % from start, % from end".format(i+1, j+1-frames).postln;
		^channel_data.collect({|chan| chan.drop(i+1).drop(j+1-frames)});
	}

	*calculateSigmaThresholds {
		var all_data = this.pr_collectOutputData();
		var threshold_data;

		~test = [];
		all_data = all_data.collect({
			|arr,i|
			arr = arr.reduce('++');
			~test = ~test.add(arr);
			arr = this.pr_smooth(arr);
			arr = arr.differentiate.drop(1);
			arr = arr.abs.sort;
			"processed: %/%".format(i+1,all_data.size).postln;
			arr;
		});

		threshold_data = all_data.collect({
			|arr,i|
			arr[arr.size * this.sigmas];
		});

		^threshold_data;
	}

	*pr_dataFilename {
		^archiveName.resolveRelative;
	}

	*writeSigmaThresholds {
		var data = this.calculateSigmaThresholds();
		data.writeArchive(this.pr_dataFilename);
		postln("Archive written.");
	}
}
