## Changes from upstream

## Akai APC 40
- USER mode selected by default instead of PAN

#### Main layer
- `PLAY` - restore automation override (automation is overridden - LED on)
- `RECORD` - toggle arranger recording
- `SESSION` - toggle master recording
- `NUDGE-` - undo
- `NUDGE+` - redo

#### Shift layer
- `SHIFT + NUDGE-` - exit track folder
- `SHIFT + NUDGE+` - enter track folder
- `SHIFT + TEMPO` - 0.01 tempo change
- `SHIFT + CUE LEVEL` - cue mix volume
- `SHIFT + SENDS` - select sends tracks only if shift is pressed

#### Clip actions
PAN / SEND / USER act only while held (double press no longer latches them).
- `PAN + CLIP` - select clip, switch to the EDIT layout and zoom the detail editor to fit
- `SEND + CLIP` - duplicate clip
- `USER + CLIP` - delete clip
- `BANK + CLIP` - cycle clip color

#### Device control buttons + LEDs changes
- `BUTTON 6` - pin both track and device
- `LED 6` - both track and device are pinned
- `SHIFT + BUTTON 6`- toggle playhead follow

- `BUTTON 7` - toggle MIX / EDIT panel layout and zoom the detail editor to fit
- `LED 7` - EDIT panel is selected
- `SHIFT + BUTTON 7` - toggle MIX / ARRANGE panel layout

- `BUTTON 8`- show device details / show VST
- `SHIFT + BUTTON 8`- toggle device remote

## All controllers
- Every automatic "zoom to fit" of the detail editor uses Bitwig's "Zoom Detail Editor to Fit Selection or All": it fits the selected notes if any are selected, otherwise the whole clip

## Komplete Kontrol mk3
- Recording focus follows the panel layout: LAUNCHER in MIX (and by default at start), ARRANGER in ARRANGE; EDIT keeps the last one
- `PLAY` - continue playback
- `SHIFT` + `RECORD` - toggle current track arm (LAUNCHER focus)
- `AUTO` - restore automation (LAUNCHER focus)
- 4D encoder press - no longer launches the clip / scene; in EDIT it zooms the detail editor to fit (selection or all), nothing in MIX / ARRANGE
- `SHIFT` + 4D encoder press - no longer stops the track / all tracks; in EDIT it switches between the Piano Roll and the Drum/Hybrid editor, nothing in MIX / ARRANGE
- While the browser is open, the 4D encoder keeps its upstream behaviour: press confirms, `SHIFT` + press cancels, turn scrolls the selection
- 4D encoder turn - scrub the play position in ARRANGE, zoom the detail editor in EDIT, nothing in MIX
- `SHIFT` + 4D encoder turn - zoom the arranger in ARRANGE, zoom the key heights of the detail editor in EDIT (to bring more pitches into view), nothing in MIX (the track volume and pan bindings are removed)
- Keyboard port: notes, pitch bend, aftertouch, mod wheel, expression, sustain and sostenuto pass on all MIDI channels (for split templates); all CCs pass on channel 2
- `Assign 1` pedal: TIP: record, RING: delete selected clip, TIP & RING: restore automation (not available with NKS Connect)

## Clip recording (Kontrol `Assign 1` TIP, MIDI Captain `D`)
- Nothing recording: switch to the EDIT layout and record a new clip on every armed track (nothing happens if no track is armed)
- Something recording: stop every recording clip (not only the selected one) and zoom the detail editor to fit
- Optional length quantize once the clips have stopped: with launch quantization `1/4` the loop length is rounded down to whole bars, with `1/8` to whole beats. Clips shorter than one bar keep their recorded length. Assumes 4/4. The detail editor is zoomed to fit again afterwards.
- Covers the first 16 top-level tracks and 32 scenes

## PaintAudio MIDI Captain (not in upstream)
Uses the configs in `src/main/resources/MidiCaptain configs`. The mode follows the panel layout: MIX or ARRANGE (EDIT keeps the last one).

| Control | MIX | ARRANGE |
|---|---|---|
| `1` short / long | clip loop start later / earlier (end stays) | arranger loop start later / earlier |
| `2` short / long | clip loop shorter / longer | arranger loop longer / shorter |
| `UP` | launch previous scene | zoom in |
| `DOWN` | launch next scene (first scene if nothing is playing) | zoom out |
| `D` | record / stop clips (see above) | toggle arranger record + automation write + overdub |
| `D` long | delete selected clip | toggle arranger loop |

Loop edits move by a bar when the launch quantization is `1/4`, otherwise by a beat. Clip loop edits also move the play start to the loop start and zoom the detail editor to fit.

Same in both modes:
- `3` - cycle expression mode: Volume, Device Param 1, Device Param 2, Pan
- `3` long - toggle opening the first device window on arm
- `4` - cycle launch quantization: `1/4`, `1/8`, `1/16`
- `4` long - toggle length quantize after record
- `A` - play / pause, `A` long - stop
- `B` - tap tempo, `B` long - toggle metronome
- `C` - arm track 1 / 2 / 3 of the active input set (unarms the others, selects the track and its first device)
- `C` long - switch between the MONO (tracks 1-3) and STEREO (tracks 4-6) input sets
- `EXP 1` - the selected track's volume / pan or the selected device's remote 1 / 2, per expression mode
- `EXP 2` - the counterpart: Pan when `EXP 1` is Volume (and vice versa), Device Param 2 when `EXP 1` is Device Param 1 (and vice versa)
- `ENCODER` - tempo, 53-180 BPM in whole steps
