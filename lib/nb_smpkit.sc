// smpKit - nb voice to play ur smpls - v.0.2.0 @sonoCircuit

NB_smpKit {

	*initClass {

		var kitGroup, kitVoices, kitBuffers, voiceParams;
		var numVoices = 12;

		StartUp.add {

			var s = Server.default;

			voiceParams = Dictionary.new;
			numVoices.do({ arg i;
				voiceParams[i] = Dictionary.newFrom([
					\mainAmp, 1,
					\amp, 0.8,
					\pan, 0,
					\dist, 0,
					\sendA, 0,
					\sendB, 0,
					\mode, 0,
					\pitch, 0,
					\tune, 0,
					\plyDir, 1,
					\rateSlew, 1,
					\srtRel, 0,
					\lenRel, 1,
					\fadeIn, 0.01,
					\fadeOut, 0.01,
					\lpfHz, 20000,
					\lpfRz, 0,
					\eqHz, 1200,
					\eqQ, 0,
					\eqAmp, 0,
					\hpfHz, 220,
					\hpfRz, 0,
				]);
			});

			s.waitForBoot {

				kitGroup = Group.new(s);
				kitVoices = Array.fill(numVoices, { Group.new(kitGroup) });
				kitBuffers = Array.fill(numVoices, nil);

				SynthDef(\smpkit_mono,{
					arg out, sendABus, sendBBus,
					mainAmp = 1, vel = 1, amp = 1, pan = 0, sendA = 0, sendB = 0, pitch = 0, gate = 1, mode = 0, bfr = 0,
					dist = 0, lpfHz = 20000, lpfRz = 0, eqHz = 1200, eqQ = 0, eqAmp = 0, hpfHz = 220, hpfRz = 0,
					tune = 0, plyDir = 0, rateSlew = 0.2, srtRel = 0, lenRel = 1, fadeIn = 0.01, fadeOut = 0.01;

					var rate, numFrames, srtFrame, endFrame, endRel, envOne, envHld, envAmp, sDur, phasePos;
					var att, boost, snd, lpfQ, hpfQ, duckFrames, duckGate, envDuck, duckTime = 0.01;

					// rescale, smooth, clamp
					tune = tune.clip(-100, 100);
					srtRel = Lag.kr(srtRel).linlin(0, 1, 0, 0.99);
					lenRel = Lag.kr(lenRel).linlin(0, 1, 0.01, 1);
					fadeIn = fadeIn.clip(0.01, 2);
					fadeOut = fadeOut.clip(0.01, 2);

					lpfHz = Lag.kr(lpfHz).clip(20, 20000);
					hpfHz = Lag.kr(hpfHz).clip(20, 20000);
					lpfQ = Lag.kr(lpfRz.linlin(0, 1, 1, 0.1));
					hpfQ = Lag.kr(hpfRz.linlin(0, 1, 1, 0.1));

					eqHz = Lag.kr(eqHz).clip(400, 16000);
					eqQ = Lag.kr(eqQ.linlin(0, 1, 2, 0.1));
					eqAmp = Lag.kr(eqAmp).clip(-12, 12);

					sendA = Lag.kr(sendA);
					sendB = Lag.kr(sendB);

					dist = Lag.kr(dist).clip(0, 1);
					boost = dist.linlin(0, 1, 12, 24);
					att = dist.linlin(0, 1, 0, -8);

					// frame math
					plyDir = Select.kr(plyDir < 0, [1, -1]);
					rate = Lag3.kr((pitch + (tune/100)).midiratio * BufRateScale.kr(bfr) * plyDir, rateSlew);
					endRel = (srtRel + lenRel).clip(0.01, 1);
					numFrames = BufFrames.ir(bfr);
					srtFrame = numFrames * Select.kr(rate > 0, [endRel, srtRel]);
					endFrame = numFrames * Select.kr(rate > 0, [srtRel, endRel]);
					duckFrames = BufSampleRate.ir(bfr) * duckTime * rate.abs;
					sDur = (numFrames * lenRel / rate.abs / s.sampleRate) - (fadeIn + fadeOut);

					// envelopes
					envOne = EnvGen.ar(Env.linen(fadeIn, sDur, fadeOut, curve: \sine), gate, doneAction: Select.kr(mode, [2, 0]));
					envHld = EnvGen.ar(Env.asr(fadeIn, 1, fadeOut, curve: \sine), gate, doneAction: Select.kr(mode, [0, 2]));
					envAmp = Select.kr(mode, [envOne, envHld]);

					// phasor
					phasePos = Phasor.ar(gate, rate, srtFrame, endFrame, srtFrame);

					// ducking
					duckGate = Select.ar(rate > 0, [
						InRange.ar(phasePos, endFrame, endFrame + duckFrames),
						InRange.ar(phasePos, endFrame - duckFrames, endFrame)
					]);
					envDuck = EnvGen.ar(Env.new([1, 0, 1], [duckTime], \sine), duckGate);

					// buffer read
					snd = BufRd.ar(1, bfr, phasePos, interpolation: 4) * -3.dbamp;

					// filters, eq and distortion
					snd = (snd * (1 - dist) + ((snd * boost.dbamp).tanh * dist)) * att.dbamp;
					snd = RHPF.ar(snd, hpfHz, hpfQ);
					snd = BPeakEQ.ar(snd, eqHz, eqQ, eqAmp);
					snd = RLPF.ar(snd, lpfHz, lpfQ);

					// output stage
					snd = snd * envDuck * envAmp;
					snd = snd * vel * amp * mainAmp;
					snd = Pan2.ar(snd, pan);

					Out.ar(out, snd);
					Out.ar(sendABus, snd * sendA);
					Out.ar(sendBBus, snd * sendB);
				}).add;

				SynthDef(\smpkit_stereo,{
					arg out, sendABus, sendBBus,
					mainAmp = 1, vel = 1, amp = 1, pan = 0, sendA = 0, sendB = 0, pitch = 0, gate = 1, mode = 0, bfr = 0,
					dist = 0, lpfHz = 20000, lpfRz = 0, eqHz = 1200, eqQ = 0, eqAmp = 0, hpfHz = 220, hpfRz = 0,
					tune = 0, plyDir = 0, rateSlew = 0.2, srtRel = 0, lenRel = 1, fadeIn = 0.01, fadeOut = 0.01;

					var rate, numFrames, srtFrame, endFrame, endRel, envOne, envHld, envAmp, sDur, phasePos;
					var att, boost, snd, lpfQ, hpfQ, duckFrames, duckGate, envDuck, duckTime = 0.01;

					// rescale, smooth, clamp
					tune = tune.clip(-100, 100);
					srtRel = Lag.kr(srtRel).linlin(0, 1, 0, 0.99);
					lenRel = Lag.kr(lenRel).linlin(0, 1, 0.01, 1);
					fadeIn = fadeIn.clip(0.01, 2);
					fadeOut = fadeOut.clip(0.01, 2);

					lpfHz = Lag.kr(lpfHz).clip(20, 20000);
					hpfHz = Lag.kr(hpfHz).clip(20, 20000);
					lpfQ = Lag.kr(lpfRz.linlin(0, 1, 1, 0.1));
					hpfQ = Lag.kr(hpfRz.linlin(0, 1, 1, 0.1));

					eqHz = Lag.kr(eqHz).clip(400, 16000);
					eqQ = Lag.kr(eqQ.linlin(0, 1, 2, 0.1));
					eqAmp = Lag.kr(eqAmp).clip(-12, 12);

					sendA = Lag.kr(sendA);
					sendB = Lag.kr(sendB);

					dist = Lag.kr(dist).clip(0, 1);
					boost = dist.linlin(0, 1, 12, 24);
					att = dist.linlin(0, 1, 0, -8);

					// frame math
					plyDir = Select.kr(plyDir < 0, [1, -1]);
					rate = Lag3.kr((pitch + (tune/100)).midiratio * BufRateScale.kr(bfr) * plyDir, rateSlew);
					endRel = (srtRel + lenRel).clip(0.01, 1);
					numFrames = BufFrames.ir(bfr);
					srtFrame = numFrames * Select.kr(rate > 0, [endRel, srtRel]);
					endFrame = numFrames * Select.kr(rate > 0, [srtRel, endRel]);
					duckFrames = BufSampleRate.ir(bfr) * duckTime * rate.abs;
					sDur = (numFrames * lenRel / rate.abs / s.sampleRate) - (fadeIn + fadeOut);

					// envelopes
					envOne = EnvGen.ar(Env.linen(fadeIn, sDur, fadeOut, curve: \sine), gate, doneAction: Select.kr(mode, [2, 0]));
					envHld = EnvGen.ar(Env.asr(fadeIn, 1, fadeOut, curve: \sine), gate, doneAction: Select.kr(mode, [0, 2]));
					envAmp = Select.kr(mode, [envOne, envHld]);

					// phasor
					phasePos = Phasor.ar(gate, rate, srtFrame, endFrame, srtFrame);

					// ducking
					duckGate = Select.ar(rate > 0, [
						InRange.ar(phasePos, endFrame, endFrame + duckFrames),
						InRange.ar(phasePos, endFrame - duckFrames, endFrame)
					]);
					envDuck = EnvGen.ar(Env.new([1, 0, 1], [duckTime], \sine), duckGate);

					// buffer read
					snd = BufRd.ar(2, bfr, phasePos, interpolation: 4) * -3.dbamp;

					// filters, eq and distortion
					snd = (snd * (1 - dist) + ((snd * boost.dbamp).tanh * dist)) * att.dbamp;
					snd = RHPF.ar(snd, hpfHz, hpfQ);
					snd = BPeakEQ.ar(snd, eqHz, eqQ, eqAmp);
					snd = RLPF.ar(snd, lpfHz, lpfQ);

					// output stage
					snd = snd * envDuck * envAmp;
					snd = snd * vel * amp * mainAmp;
					snd = Pan2.ar(snd, pan);

					Out.ar(out, snd);
					Out.ar(sendABus, snd * sendA);
					Out.ar(sendBBus, snd * sendB);
				}).add;

				OSCFunc.new({ |msg|
					var vox = msg[1].asInteger;
					var vel = msg[2].asFloat;
					if (kitBuffers[vox].notNil) {
						var def = if (kitBuffers[vox].numChannels > 1) {\smpkit_stereo} {\smpkit_mono};
						kitVoices[vox].set(\gate, -1.05);
						Synth.new(def,
							[
								\vel, vel,
								\bfr, kitBuffers[vox],
								\sendABus, ~sendA ? s.outputBus,
								\sendBBus, ~sendB ? s.outputBus,
							] ++ voiceParams[vox].getPairs, target: kitVoices[vox]
						);
					};
				}, "/nb_smpkit/trig");

				OSCFunc.new({ |msg|
					var vox = msg[1].asInteger;
					kitVoices[vox].set(\gate, 0);
				}, "/nb_smpkit/stop");

				OSCFunc.new({ |msg|
					kitGroup.set(\gate, -1.05);
				}, "/nb_smpkit/panic");

				OSCFunc.new({ |msg|
					var val = msg[1].asFloat;	
					kitGroup.set(\mainAmp, val);
					numVoices.do({ |vox|
						voiceParams[vox][\mainAmp] = val;
					});
				}, "/nb_smpkit/set_level");

				OSCFunc.new({ |msg|
					var vox = msg[1].asInteger;
					var key = msg[2].asSymbol;
					var val = msg[3].asFloat;
					kitVoices[vox].set(key, val);
					voiceParams[vox][key] = val;
				}, "/nb_smpkit/set_param");

				OSCFunc.new({ |msg|
					var vox = msg[1].asInteger;
					var path = msg[2].asString;
					if (kitBuffers[vox].notNil) { if(kitBuffers[vox].bufnum.notNil) { kitBuffers[vox].free } };
					kitBuffers[vox] = Buffer.read(s, path);
				}, "/nb_smpkit/load_buffer");

				OSCFunc.new({ |msg|
					var vox = msg[1].asInteger;
					kitVoices[vox].set(\gate, -1);
					kitBuffers[vox].free;
					kitBuffers[vox] = nil;
				}, "/nb_smpkit/clear_buffer");

				OSCFunc.new({ |msg|
					numVoices.do({ |vox|
						kitVoices[vox].set(\gate, -1);
						kitBuffers[vox].free;
						kitBuffers[vox] = nil;
					});
					"nb_smpkit buffers freed".postln;
				}, "/nb_smpkit/free_buffers");

				OSCFunc.new({ |msg|
					numVoices.do({ |vox|
						kitBuffers[vox].free;
						kitBuffers[vox] = nil;
					});
					kitGroup.free;
				}, "/nb_smpkit/free_all");

			}
		}
	}
}
