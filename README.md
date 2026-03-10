# smpKit

an nb player to trigger and loop samples with the following features:

- 12 slots/samples/voices
- max 5min each
- mono/stereo
- compensates for different sample rates
- clickless looping
- 2 kit modes (classic, instrument)
- 3 play modes (oneshot, hold, toggle)
- supports `nb:modulate` and `nb:pitch_bend`
- resonant lpf/hpf and eq
- global editing of all 12 voices
- save and load presets/kits

## **instructions:**

install and activate smpKit like other mods. load a script that supports nb players and select smpKit.

## **classic mode:**

in classic mode each voice is triggered by a midi note. voice 1 is triggered by the note set under `root note` and all subsequent voices are triggered by acending midi notes. So, if the root note is set to `C` voice 1 will be triggerd by `C` voice 2 by `C#` etc.. the octave is irrelevant as it wraps.

