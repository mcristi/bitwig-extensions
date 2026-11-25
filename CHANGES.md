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

#### Mapping mode for the project remote controls (USER knobs)
- While `USER` is held the track select LEDs are off
- `USER + SELECT 1-8` - mapping mode for the project remote control on that top knob: the knob's LED ring and that select button flash together and the next parameter touched in Bitwig (by mouse, from another controller, or with a device control knob of the APC40) is mapped to it. The ring then shows its value
- Turning a device control knob of the APC40, or a parameter knob of the Kontrol S Mk3 (device / track / project remotes), counts as touching for Bitwig devices too: while a mapping waits, those knobs touch the remote control they turn (a plug-in reports touches itself, a Bitwig device's remote control set from a controller does not)
- If that control is mapped already, a new page of project remote controls is created, the knobs switch to it and the control is mapped there (Bitwig does not let a script delete or overwrite a mapping; the old page stays and can be removed in Bitwig)
- The mapping mode only lasts while `USER` is held: keep it held until the parameter is touched. Releasing `USER` cancels, as do `USER` + the same select button, `PAN` / `SENDS`, and changing the project remotes page (`USER + SCENE`). `USER` + another select button moves the mapping mode to that knob

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

## Allen & Heath Xone:K3 (not upstream's script)
A new extension by mcristi (not Bitwig's own Xone:K3 script) that controls one FX Chain. It is detected on
the same ports as Bitwig's script: choose `bitwig-extensions-by-meli` in the drop-down of the Xone:K3 controller.
The unit runs its factory map (MIDI channel 15, latching layers off).

- Pin `K3 FX Track` and `K3 FX Device` on the Chain in the controller's panel
- The controls follow the Chain's remote control pages by page name, which must match exactly (upper and
  lower case too). A page that is missing leaves its controls without function

#### Pages
- `encoders` - controls 1-4 on the top encoders
- `eq` - the high, mid and low band down the first column of knobs, high on top: gains are controls
  1-3, band on/off controls 5-7 on the buttons below them, in blue, yellow and red
- `eq shift` - while `SHIFT` is held, the same three knobs control the frequencies: control 3 (high
  frequency) on the high knob, 2 on the mid knob, 1 (low frequency) on the low knob. The high and low band buttons
  keep working, the mid band button is off meanwhile
- `guitar rig` - the second and third row of knobs in columns 3 and 4: control 1 (reverb) and 2 (delay)
  down column 3, 3 and 4 down column 4. Below the delay knob control 6 (delay freeze, light blue),
  below control 4 control 8 (FX on/off, green with a reversed light: bright while its parameter is off,
  dim while it is on)
- `looper` - controls 1-4 on the first row of the button matrix. Only one is on at a time
- `shaperbox` - controls 1-8 on the second and third row of the button matrix. Only one is on at a time:
  switching one on switches the others off

#### Faders
- `1` - control 2 of the page `mix`, meant for the Chain's own Mix knob (wet/dry of all effects)
- `2` - mix of the device named `Shaperbox` inside the Chain (control 2 of its remote controls)
- `3` - mix of `Pitch Grain`
- `4` - mix of `Guitar Rig`
- The devices are found by name, exactly; a missing device leaves its fader without function
- No pickup: moving a fader sets the mix to the fader's position at once

#### Encoders
- Turning an encoder on an automated parameter overrides the automation: its LED goes from dim to bright
  (yellow on encoders 1 and 2, orange on 3, light blue on 4)
- Pushing the encoder restores the automation of that parameter and dims the LED. Turning it again
  starts from the value the automation has at that moment

#### Buttons
- On/off: bright when on, dim when off, dark when the page has no control there
- Knobs pick the value up before they change it

#### Last row of the button matrix
- `1` - restore all automation (bright orange while any automation is overridden, dim otherwise)
- `2` - unmute all pads of the Drum Machine, the first device next to the Chain (bright red while a pad
  is muted, dim otherwise)
- `3` - not used
- `4` - fill, while held
