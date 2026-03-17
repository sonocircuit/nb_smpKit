# smpKit

An nb player to trigger and loop samples with the following features:

- 12 slots / samples / voices
- max 5 min each
- mono / stereo
- compensates for different sample rates
- clickless looping
- two smpKit modes (`classic`, `instrument`)
- three sample playback modes (`oneshot`, `hold`, `toggle`)
- global editing of all 12 voices
- supports `nb:modulate` and `nb:pitch_bend`
- resonant LPF / HPF and EQ
- save and load presets / kits
- fx sends for fx mod (hidden when fx mod is not active)

## **instructions:**

- Install and activate smpKit like other mods. Load a script that supports nb players and select smpKit.
- smpKit initializes in an empty state. Complete states can be recalled under `presets`. When smpKit is active and a pset of the active script is saved, all parameters are saved as well. Total recall can be achieved either with a scripts' psets or via save/load from the parameter menu.
- Under `settings`, the global parameters for `main level`, `root note`, `pitchbend`, and `type` are accessed. Use `type` to switch between `classic` and `instrument` mode.
- Under `voice`, `levels`, `playback`, and `filters`, all parameters of the currently selected voice are accessed. When the selected voice is set to `all`, parameter changes affect all 12 voices.
- Under `modulation`, global morphing of `drive`, `lpf cutoff`, `hpf cutoff`, `send a`, and `send b` is accessed. Set the relative change ±0–100% of the parameters that will be affected either via `nb:modulate` (e.g., with sidvagn) or by MIDI-mapping the `mod amt` parameter. e.g. setting `lpf cutoff` to -100% will sweep from 20kHz to 20Hz if the initial cutoff is at it's max. the morphing always occurs from the current parameter value and is clamped within the parameter's range.

### **classic mode:**

In classic mode, each voice is triggered by a MIDI note. Voice 1 is triggered by the note set under `root note`, and all subsequent voices are triggered by ascending MIDI notes. So if the root note is set to C, voice 1 will be triggered by C, voice 2 by C#, etc. The octave is irrelevant, as it wraps around.

### **instrument mode:**

In instrument mode, smpKit can be used as a lo-fi, sample-based instrument. The voices and samples are decoupled, which allows up to 6-voice polyphony over multiple octaves. When in instrument mode, smpKit expects all 12 voices to be populated with samples ascending by 1 semitone. For example, voice 1 is populated with a sample pitched at A2, voice 2 with A#2, voice 3 with B2, etc.

To avoid tedious menu scrolling, I recommend putting your samples in a folder and labeling them so they appear in sequential order. When `selected voice` is set to `all`, sample loading (`load collection`) will populate all voices with subsequent samples . For example, selecting the A2 file will populate voice 1 with A2, voice 2 with A#2, etc. Since our first sample is A2, we need to set the root note to A2 so that the MIDI note corresponds to the sample pitch. In instrument mode only `oneshot` and `hold` play modes are supported, when set to `toggle`, `hold` mode is used.
<br>

**TIPP**: set playmode to `hold` and shorten the length of the sample to get instant SK-1-vibes.
