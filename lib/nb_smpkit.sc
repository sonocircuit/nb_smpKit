// smpKit - nb voice to play ur smpls - v.0.1.0 @sonoCircuit

NB_smpKit {

	*initClass {

		var drmGroup, drmVoices, drmBuffers, voiceParams;
		var numVoices = 12;

		StartUp.add {

			var s = Server.default;

			voiceParams = Dictionary.new;
			numVoices.do({ arg i;
				voiceParams[i] = Dictionary.newFrom([
					\amp, 0.8,
					\pan, 0,
					\sendA, 0,
					\sendB, 0,
					\fadeIn, 0.01,
					\fadeOut, 0.01,
					\mode, 0,
					\rate, 1,
					\rateSlew, 1,
					\srtRel, 0,
					\lenRel, 1,
					\hiFreq, 1,
					\midFreq, 1,
					\midAmp, 1,
					\loFreq, 1
				]);
			});

			s.waitForBoot {

				drmGroup = Group.new(s);
				drmVoices = Array.fill(numVoices, { Group.new(drmGroup) });
				drmBuffers = Array.fill(numVoices, nil);

				SynthDef(\smpkit_mono, {
					arg out = 0, sendABus = 0, sendBBus = 0,
					vel = 0.8, amp = 1, pan = 0, sendA = 0, sendB = 0,
					gate = 1, fadeIn = 0.01, fadeOut = 0.1, mode = 0,
					bfr = 0, rate = 1, rateSlew = 0.2, srtRel = 0, lenRel = 1,
					hiFreq = 20, midFreq = 1200, midAmp = 0, loFreq = 18000;

					var numFrames, srtFrame, endFrame, endRel, envOne, envHld, envAmp, sDur, phasePos, snd;
					var duckFrames, duckGate, envDuck, duckTime = 0.01;

					amp = Lag3.kr(amp).clip(0, 1);
					pan = Lag3.kr(pan).clip(-1, 1);
					hiFreq = Lag3.kr(hiFreq).clip(20, 20000);
					midFreq = Lag3.kr(midFreq).clip(20, 20000);
					midAmp = Lag3.kr(midAmp).clip(-18, 18);
					loFreq = Lag3.kr(loFreq).clip(20, 20000);
					rate = Lag3.kr(rate * BufRateScale.kr(bfr), rateSlew);

					srtRel = Lag3.kr(srtRel).clip(0, 0.99);
					lenRel = Lag3.kr(lenRel).clip(0.01, 1);
					endRel = Lag3.kr(srtRel + lenRel).clip(0.01, 1);

					numFrames = BufFrames.ir(bfr);
					srtFrame = numFrames * Select.kr(rate > 0, [endRel, srtRel]);
					endFrame = numFrames * Select.kr(rate > 0, [srtRel, endRel]);
					duckFrames = BufSampleRate.ir(bfr) * duckTime * rate.abs;
					sDur = (numFrames * lenRel / rate.abs / s.sampleRate) - (fadeIn + fadeOut);

					envOne = EnvGen.ar(Env.linen(fadeIn, sDur, fadeOut, curve: \sine), gate, doneAction: Select.kr(mode, [2, 0]));
					envHld = EnvGen.ar(Env.asr(fadeIn, 1, fadeOut, curve: \sine), gate, doneAction: Select.kr(mode, [0, 2]));
					envAmp = Select.kr(mode, [envOne, envHld]);

					phasePos = Phasor.ar(gate, rate, srtFrame, endFrame, srtFrame);

					duckGate = Select.ar(rate > 0, [
						InRange.ar(phasePos, endFrame, endFrame + duckFrames),
						InRange.ar(phasePos, endFrame - duckFrames, endFrame)
					]);

					envDuck = EnvGen.ar(Env.new([1, 0, 1], [duckTime], \sine), duckGate);

					snd = BufRd.ar(1, bfr, phasePos, interpolation: 4) * -3.dbamp;
					snd = RHPF.ar(snd, hiFreq, 0.8);
					snd = BPeakEQ.ar(snd, midFreq, db: midAmp);
					snd = RLPF.ar(snd, loFreq, 0.8);
					snd = snd * envDuck;
					snd = snd * amp * vel * envAmp;
					snd = Pan2.ar(snd, pan);

					Out.ar(out, snd);
					Out.ar(sendABus, sendA * snd);
					Out.ar(sendBBus, sendB * snd);
				}).add;

				SynthDef(\smpkit_stereo, {
					arg out = 0, sendABus = 0, sendBBus = 0,
					vel = 0.8, amp = 1, pan = 0, sendA = 0, sendB = 0,
					gate = 1, fadeIn = 0.01, fadeOut = 0.1, mode = 0,
					bfr = 0, rate = 1, rateSlew = 0.2, srtRel = 0, lenRel = 1,
					hiFreq = 20, midFreq = 1200, midAmp = 0, loFreq = 18000;

					var numFrames, srtFrame, endFrame, endRel, envOne, envHld, envAmp, sDur, phasePos, snd;
					var duckFrames, duckGate, envDuck, duckTime = 0.01;

					amp = Lag3.kr(amp).clip(0, 1);
					pan = Lag3.kr(pan).clip(-1, 1);
					hiFreq = Lag3.kr(hiFreq).clip(20, 20000);
					midFreq = Lag3.kr(midFreq).clip(20, 20000);
					midAmp = Lag3.kr(midAmp).clip(-18, 18);
					loFreq = Lag3.kr(loFreq).clip(20, 20000);
					rate = Lag3.kr(rate * BufRateScale.kr(bfr), rateSlew);

					srtRel = Lag3.kr(srtRel).clip(0, 0.99);
					lenRel = Lag3.kr(lenRel).clip(0.01, 1);
					endRel = Lag3.kr(srtRel + lenRel).clip(0.01, 1);

					numFrames = BufFrames.ir(bfr);
					srtFrame = numFrames * Select.kr(rate > 0, [endRel, srtRel]);
					endFrame = numFrames * Select.kr(rate > 0, [srtRel, endRel]);
					duckFrames = BufSampleRate.ir(bfr) * duckTime * rate.abs;
					sDur = (numFrames * lenRel / rate.abs / s.sampleRate) - (fadeIn + fadeOut);

					envOne = EnvGen.ar(Env.linen(fadeIn, sDur, fadeOut, curve: \sine), gate, doneAction: Select.kr(mode, [2, 0]));
					envHld = EnvGen.ar(Env.asr(fadeIn, 1, fadeOut, curve: \sine), gate, doneAction: Select.kr(mode, [0, 2]));
					envAmp = Select.kr(mode, [envOne, envHld]);

					phasePos = Phasor.ar(gate, rate, srtFrame, endFrame, srtFrame);

					duckGate = Select.ar(rate > 0, [
						InRange.ar(phasePos, endFrame, endFrame + duckFrames),
						InRange.ar(phasePos, endFrame - duckFrames, endFrame)
					]);

					envDuck = EnvGen.ar(Env.new([1, 0, 1], [duckTime], \sine), duckGate);

					snd = BufRd.ar(2, bfr, phasePos, interpolation: 4) * -3.dbamp;
					snd = RHPF.ar(snd, hiFreq, 0.8);
					snd = BPeakEQ.ar(snd, midFreq, db: midAmp);
					snd = RLPF.ar(snd, loFreq, 0.8);
					snd = snd * envDuck;
					snd = snd * amp * vel * envAmp;
					snd = Balance2.ar(snd[0], snd[1], pan);

					Out.ar(out, snd);
					Out.ar(sendABus, sendA * snd);
					Out.ar(sendBBus, sendB * snd);
				}).add;

				OSCFunc.new({ |msg|
					var vox = msg[1].asInteger;
					var vel = msg[2].asFloat;
					if (drmBuffers[vox].notNil) {
						var def = if (drmBuffers[vox].numChannels > 1) {\smpkit_stereo} {\smpkit_mono};
						drmVoices[vox].set(\gate, -1.05);
						Synth.new(def,
							[
								\vel, vel,
								\bfr, drmBuffers[vox],
								\sendABus, ~sendA ? s.outputBus,
								\sendBBus, ~sendB ? s.outputBus,
							] ++ voiceParams[vox].getPairs, target: drmVoices[vox]
						);
					};
				}, "/nb_smpkit/trig");

				OSCFunc.new({ |msg|
					var vox = msg[1].asInteger;
					drmVoices[vox].set(\gate, 0);
				}, "/nb_smpkit/stop");

				OSCFunc.new({ |msg|
					drmGroup.set(\gate, -1.05);
				}, "/nb_smpkit/panic");

				OSCFunc.new({ |msg|
					var vox = msg[1].asInteger;
					var key = msg[2].asSymbol;
					var val = msg[3].asFloat;
					drmVoices[vox].set(key, val);
					voiceParams[vox][key] = val;
				}, "/nb_smpkit/set_param");

				OSCFunc.new({ |msg|
					var vox = msg[1].asInteger;
					var path = msg[2].asString;
					if (drmBuffers[vox].notNil) { if(drmBuffers[vox].bufnum.notNil) { drmBuffers[vox].free } };
					drmBuffers[vox] = Buffer.read(s, path);
				}, "/nb_smpkit/load_buffer");

				OSCFunc.new({ |msg|
					var vox = msg[1].asInteger;
					drmVoices[vox].set(\gate, -1);
					drmBuffers[vox].free;
					drmBuffers[vox] = nil;
				}, "/nb_smpkit/clear_buffer");

				OSCFunc.new({ |msg|
					numVoices.do({ |vox|
						drmVoices[vox].set(\gate, -1);
						drmBuffers[vox].free;
						drmBuffers[vox] = nil;
					});
					"nb_smpkit buffers freed".postln;
				}, "/nb_smpkit/free_buffers");

				OSCFunc.new({ |msg|
					numVoices.do({ |vox|
						drmBuffers[vox].free;
						drmBuffers[vox] = nil;
					});
					drmGroup.free;
				}, "/nb_smpkit/free_all");

			}
		}
	}
}