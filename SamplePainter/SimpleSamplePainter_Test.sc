SimpleSamplePainter_Test : UnitTest {
	*pattern {
		^FloatArray[0, 0.5, 1, -1, -0.5, 0];
	}

	*pattern2 {
		^FloatArray[0.125, 0.25, 0.75, -0.125, -0.25, -0.75];
	}

	*isTestPatternMatch {
		arg pat;
		^pat == this.pattern;
	}

	*isTestPattern2Match {
		arg pat;
		^pat = this.pattern2;
	}

	testFile {
		^this.testDir +/+ "test.wav";
	}

	testDir {
		^"test_audio".resolveRelative;
	}

	path_2ch96 {^this.testDir +/+ "bach_stereo_96.wav"}
	path_2ch44 {^this.testDir +/+ "beet_stereo_44.wav"}
	path_1ch96 {^this.testDir +/+ "bach_mono_96.wav"}
	path_1ch44 {^this.testDir +/+ "beet_mono_44.wav"}

	setUp {
		if(File.exists(this.testFile)) {File.delete(this.testFile)};
		SimpleSamplePainter.nChannels_(2);
		SimpleSamplePainter.sr_(44100);
	}

	tearDown {
		if(File.exists(this.testFile)) {File.delete(this.testFile)};
		SimpleSamplePainter.nChannels_(2);
		SimpleSamplePainter.sr_(44100);
	}

	createMU {
		var sp = SimpleSamplePainter(this.testFile, 10000, [], [], []);
		^sp;
	}

	test_chooseCutFile {
		var sp = SimpleSamplePainter(this.testFile, 1000, [this.path_2ch96 -> 1, this.path_2ch44 -> 0], [], []);
		var arr;

		// 1-0 probability returns only first result
		10.do {
			this.assertEquals(sp.chooseCutFile, this.path_2ch96, "probability of 1 should always work");
		};

		sp = SimpleSamplePainter(this.testFile, 1000, [this.path_2ch96 -> 0, this.path_2ch44 -> 1], [], []);
		// 0-1 probability returns only first result
		10.do {
			this.assertEquals(sp.chooseCutFile, this.path_2ch44, "probability of 1 should always work");
		};

		sp = SimpleSamplePainter(this.testFile, 1000, [this.path_2ch96 -> 1, this.path_2ch44 -> 1], [], []);
		// 1-1 probability returns roughly half
		arr = 100.collect {sp.chooseCutFile()};
		this.assert(arr.any(_==this.path_2ch96) && arr.any(_==this.path_2ch44), "results of 50-50 split: %-%".format(arr.occurrencesOf(this.path_2ch96), arr.occurrencesOf(this.path_2ch44)));
	}

	test_choosePasteFunc {
		var pf1 = PasteFunc(\paste, SimpleSamplePainter.paste_replace, 1);
		var pf2 = PasteFunc(\add, SimpleSamplePainter.paste_add, 0);

		var sp = SimpleSamplePainter(this.testFile, 1000, [], [], [pf1, pf2]);
		var arr;

		this.assertEquals(pf1.probability(nil), 1);
		this.assertEquals(pf2.probability(nil), 0);
		// 1-0 probability returns only first result
		10.do {
			this.assertEquals(sp.choosePasteFunc, pf1, "probability of 1 should always work");
		};

		pf1 = PasteFunc(\paste, SimpleSamplePainter.paste_replace, 0);
		pf2 = PasteFunc(\add, SimpleSamplePainter.paste_add, 1);
		sp = SimpleSamplePainter(this.testFile, 1000, [], [], [pf1, pf2]);
		// 0-1 probability returns only first result
		10.do {
			this.assertEquals(sp.choosePasteFunc, pf2, "probability of 1 should always work");
		};

		pf1 = PasteFunc(\paste, SimpleSamplePainter.paste_replace, 1);
		pf2 = PasteFunc(\add, SimpleSamplePainter.paste_add, 1);
		sp = SimpleSamplePainter(this.testFile, 1000, [], [], [pf1, pf2]);
		// 1-1 probability returns roughly half
		arr = 100.collect {sp.choosePasteFunc()};
		this.assert(arr.includes(pf1) && arr.includes(pf2), "results of 50-50 split: %-%".format(arr.occurrencesOf(pf1), arr.occurrencesOf(pf2)));
	}

	test_pasteFunc_replace_mono {
		postln("------ testing replace: MONO -------");
		this.doTestPasteFunc_replace(1);
	}

	test_pasteFunc_replace_stereo {
		postln("------ testing replace: STEREO -------");
		this.doTestPasteFunc_replace(2);
	}

	test_pasteFunc_replace_quad {
		postln("------ testing replace: QUAD -------");
		this.doTestPasteFunc_replace(4);
	}

	test_pasteFunc_replace_octo {
		postln("------ testing replace: OCTO -------");
		this.doTestPasteFunc_replace(8);
	}

	test_pasteFunc_add_mono {
		postln("------ testing add: MONO -------");
		this.doTestPasteFunc_add(1);
	}

	test_pasteFunc_add_stereo {
		postln("------ testing add: STEREO -------");
		this.doTestPasteFunc_add(2);
	}

	test_pasteFunc_add_quad {
		postln("------ testing add: QUAD -------");
		this.doTestPasteFunc_add(4);
	}

	test_pasteFunc_add_octo {
		postln("------ testing add: OCTO -------");
		this.doTestPasteFunc_add(8);
	}


	doTestPasteFunc_replace {
		arg nch;
		var pf = PasteFunc(\replace, SimpleSamplePainter.paste_replace, 1);
		var sp, sf, arr, framelist;
		SimpleSamplePainter.nChannels_(nch);
		sp = SimpleSamplePainter(this.testFile, 10000, [], [], [pf]);
		// paste in all 1's (also tests end-of-file paste)
		sp.doPaste(pf, 0, {FloatArray.fill(10000, 1)}!nch);
		sp.doPaste(pf, 100, {this.class.pattern}!nch);
		sp.doPaste(pf, 300, {|n| n.even.if {this.class.pattern + n} {this.class.pattern2 + n}}!nch);
		sp.doPaste(pf, 500, {FloatArray.fill(1000, 0)}!nch);
		sp.doPaste(pf, 9000, {this.class.pattern}!nch);

		// read in file and verify results
		sf = SoundFile.openRead(this.testFile);

		// region 1: all 1's
		framelist = [100, 6, 300-106, 6, 500-306, 1000, 9000-1500, 6, 10000-9006];
		framelist.do {
			|frames, i|
			sf.readData(arr = FloatArray.newClear(frames * nch));
			this.assert(arr.size == (frames*nch), "region % size: %".format(i+1, frames));
			case
			// 0: 1s
			// 1: test
			// 2: 1s
			// 3: elaborate test
			// 4: 1s
			// 5: 0s
			// 6: 1s
			// 7: test
			// 8: 1s
			{i.even} {
				this.assert(arr.every(_==1), "region %: all 1s".format(i+1))
			}
			{i==5} {
				this.assert(arr.every(_==0), "region %: all 0s".format(i+1))
			}
			{(i==1) || (i==7)} {
				// test
				var flag = true;
				for(0, arr.size-1) {
					|j|
					flag = flag && (arr[j] == this.class.pattern[j.div(nch)]);
				};
				this.assert(flag, "region %: test pattern".format(i+1));
			}
			{
				// elaborate test
				var flag = true;
				for(0, arr.size-1) {
					|j|
					var samp = (j % nch).even.if
					{this.class.pattern[j.div(nch)]}
					{this.class.pattern2[j.div(nch)]};
					samp = samp + (j % nch);
					flag = flag && (arr[j] == samp);
				};
				this.assert(flag, "region %: elaborate test".format(i+1));
			}
		};

		sf.readData(arr = FloatArray.newClear(1));
		this.assert(arr.size == 0, "end of file, should not read further data!");
		sf.close;
	}

	doTestPasteFunc_add {
		arg nch;
		var pf = PasteFunc(\add, SimpleSamplePainter.paste_add, 1);
		var sp, sf, arr, framelist;
		SimpleSamplePainter.nChannels_(nch);
		sp = SimpleSamplePainter(this.testFile, 10000, [], [], [pf]);
		// paste in all 1's (also tests end-of-file paste)
		sp.doPaste(pf, 0, {FloatArray.fill(10000, 1)}!nch);
		sp.doPaste(pf, 100, {this.class.pattern}!nch);
		sp.doPaste(pf, 300, {|n| n.even.if {this.class.pattern + n} {this.class.pattern2 + n}}!nch);
		sp.doPaste(pf, 500, {FloatArray.fill(1000, 1)}!nch);
		sp.doPaste(pf, 2500, {FloatArray.fill(1000, -1)}!nch);
		sp.doPaste(pf, 9000, {this.class.pattern}!nch);

		// read in file and verify results
		sf = SoundFile.openRead(this.testFile);

		// region 1: all 1's
		framelist = [100, 6, 300-106, 6, 500-306, 1000, 1000, 1000, 9000-3500, 6, 10000-9006];
		framelist.do {
			|frames, i|
			sf.readData(arr = FloatArray.newClear(frames * nch));
			this.assert(arr.size == (frames*nch), "region % size: %".format(i+1, frames));
			case
			// 0: 1s
			// 1: test
			// 2: 1s
			// 3: elaborate test
			// 4: 1s
			// 5: 2s
			// 6: 1s
			// 7: 0s
			// 8: 1s
			// 9: test
			// 10: 1s
			{i.even} {
				this.assert(arr.every(_==1), "region %: all 1s".format(i+1))
			}
			{i==5} {
				this.assert(arr.every(_==2), "region %: all 0s".format(i+1))
			}
			{i==7} {
				this.assert(arr.every(_==0), "region %: all 0s".format(i+1))
			}
			{(i==1) || (i==9)} {
				// test
				var flag = true;
				for(0, arr.size-1) {
					|j|
					flag = flag && (arr[j] == (this.class.pattern[j.div(nch)]+1));
				};
				this.assert(flag, "region %: test pattern".format(i+1));
			}
			{
				// elaborate test
				var flag = true;
				for(0, arr.size-1) {
					|j|
					var samp = (j % nch).even.if
					{this.class.pattern[j.div(nch)]}
					{this.class.pattern2[j.div(nch)]};
					samp = samp + (j % nch) + 1;
					flag = flag && (arr[j] == samp);
				};
				this.assert(flag, "region %: elaborate test".format(i+1));
			}
		};

		sf.readData(arr = FloatArray.newClear(1));
		this.assert(arr.size == 0, "end of file, should not read further data!");
		sf.close;
	}

	// test the full cycle
	test_fullCycle {
		var sp, modifyFunc, pasteFunc, sourceFile;
		var pasteFrame, data, cutDur, cutFrame, sf, outsf, arr, arr2;
		var outputDur = 20 * SimpleSamplePainter.sr;
		sourceFile = this.path_2ch96;
		modifyFunc = ModifyFunc(\do_nothing, SimpleSamplePainter.modify_doNothing, 1);
		pasteFunc = PasteFunc(\replace, SimpleSamplePainter.paste_replace, 1);
		SimpleSamplePainter.nChannels_(2);
		sp = SimpleSamplePainter(this.testFile, outputDur, [sourceFile -> 1], [modifyFunc], [pasteFunc]);

		sp.cycle();

		pasteFrame = sp.pasteFrame;
		cutDur = sp.cutDur;
		cutFrame = sp.cutFrame;
		data = sp.data;

		// tests on paste frame
		this.assert(pasteFrame <= (SimpleSamplePainter.sr*SimpleSamplePainter.sCutDur_hi), report:false);
		this.assert(pasteFrame >= 0, report:false);
		postln("pasteframe: %".format(pasteFrame));

		// tests on cut dur
		this.assert(cutDur >= (SimpleSamplePainter.sCutDur_low * SimpleSamplePainter.sr), report:false);
		this.assert(cutDur <= (SimpleSamplePainter.sCutDur_hi * SimpleSamplePainter.sr));
		postln("cut dur: %".format(cutDur));

		// tests on cut frame
		this.assert(cutFrame >= 0, report:false);
		sf = SoundFile.openRead(this.path_2ch96);
		this.assert(cutFrame < (sf.numFrames - (SimpleSamplePainter.sCutDur_hi * SimpleSamplePainter.sr)));

		// tests on data
		this.assertEquals(data.size, 2, report:false);
		this.assertEquals(data[1].size, data[0].size, report:false);
		this.assertEquals(data[0].size, cutDur, report:false);

		// test output file in 3 regions: pre-paste, paste, post-paste
		outsf = SoundFile.openRead(this.testFile);

		// pre-paste region
		arr = FloatArray.newClear(pasteFrame * 2);
		arr2 = arr.deepCopy();
		outsf.readData(arr);
		this.assertEquals(arr, arr2, "pre-paste should match (all 0)");

		// paste region
		arr = FloatArray.newClear(cutDur * 2);
		arr2 = arr.deepCopy();
		outsf.readData(arr);
		sf.seek(cutFrame);
		sf.readData(arr2);
		sf.close;
		this.assertEquals(arr, arr2, "input cut should match output paste");
		this.assertEquals(arr, FloatArray.newFrom(data.flop.flat), "output paste should match data stored in sp");

		// post-paste region
		arr = FloatArray.newClear((outputDur-(pasteFrame+cutDur)) * 2);
		arr2 = arr.deepCopy();
		outsf.readData(arr);
		this.assertEquals(arr, arr2, "post-paste should match (all 0)");
	}


}