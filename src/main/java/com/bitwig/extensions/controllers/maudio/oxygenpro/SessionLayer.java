package com.bitwig.extensions.controllers.maudio.oxygenpro;

import com.bitwig.extension.controller.api.*;
import com.bitwig.extensions.controllers.maudio.oxygenpro.control.PadButton;
import com.bitwig.extensions.framework.Layer;
import com.bitwig.extensions.framework.Layers;
import com.bitwig.extensions.framework.di.Activate;
import com.bitwig.extensions.framework.di.Component;
import com.bitwig.extensions.framework.values.BooleanValueObject;

import java.util.Arrays;
import java.util.List;

@Component
public class SessionLayer {

   private final Layer mainLayer;

   private final RgbColor[] slotColors = new RgbColor[16];
   private final BooleanValueObject shiftActive;
   private final SceneBank sceneBank;
   private boolean overdubEnabled;
   private RgbColor trackColor = RgbColor.OFF;
   private final int numberOfTracks;

   public SessionLayer(Layers layers, HwElements hwElements, ViewControl viewControl, Transport transport) {
      Arrays.fill(slotColors, RgbColor.OFF);

      transport.isClipLauncherOverdubEnabled().addValueObserver(overdubEnabled -> this.overdubEnabled = overdubEnabled);
      this.mainLayer = new Layer(layers, "SESSION_LAYER");
      TrackBank trackBank = viewControl.getMixerTrackBank();
      List<PadButton> gridButtons = hwElements.getPadButtons();
      trackBank.setShouldShowClipLauncherFeedback(true);
      this.numberOfTracks = trackBank.getSizeOfBank();
      for (int tInd = 0; tInd < numberOfTracks; tInd++) {
         final int trackIndex = tInd;
         Track track = trackBank.getItemAt(tInd);
         prepareTrack(track);
         for (int sInd = 0; sInd < 2; sInd++) {
            final int sceneIndex = sInd;
            final ClipLauncherSlot slot = track.clipLauncherSlotBank().getItemAt(sInd);
            int buttonIndex = sceneIndex * numberOfTracks + trackIndex;
            prepareSlot(slot, buttonIndex);
            PadButton button = gridButtons.get(buttonIndex);
            button.bindLight(mainLayer, () -> this.getRgbState(track, slot, trackIndex, sceneIndex));
            button.bindPressed(mainLayer, () -> this.handlePress(track, slot, trackIndex, sceneIndex));
         }
      }
      sceneBank = trackBank.sceneBank();
      this.shiftActive = hwElements.getShiftActive();
      hwElements.getButton(OxygenCcAssignments.SCENE_LAUNCH1).bind(mainLayer, () -> sceneBank.getScene(0).launch());
      hwElements.getButton(OxygenCcAssignments.SCENE_LAUNCH2).bind(mainLayer, () -> sceneBank.getScene(1).launch());
      hwElements.getButton(OxygenCcAssignments.BANK_LEFT).bindRepeatHold(mainLayer, () -> trackBank.scrollBackwards());
      hwElements.getButton(OxygenCcAssignments.BANK_RIGHT).bindRepeatHold(mainLayer, () -> trackBank.scrollForwards());
      hwElements.bindEncoder(mainLayer, hwElements.getMainEncoder(), this::handleEncoder);
   }

   private void handleEncoder(int dir) {
      if (dir < 0) {
         sceneBank.scrollBackwards();
      } else {
         sceneBank.scrollForwards();
      }
   }

   private void prepareTrack(Track track) {
      track.isQueuedForStop().markInterested();
      track.arm().markInterested();
      track.color().addValueObserver((r, g, b) -> trackColor = RgbColor.toColor(r, g, b));
   }

   private void handlePress(Track track, ClipLauncherSlot slot, int trackIndex, int sceneIndex) {
      slot.launch();
   }

   private InternalHardwareLightState getRgbState(Track track, ClipLauncherSlot slot, int trackIndex, int sceneIndex) {
      if (slot.hasContent().get()) {
         int buttonIndex = sceneIndex * numberOfTracks + trackIndex;
         RgbColor color = slotColors[buttonIndex];
         if (slot.isRecordingQueued().get()) {
            return RgbColor.RED.getBlink();
         } else if (slot.isRecording().get()) {
            return RgbColor.RED.getBlink();
         } else if (slot.isPlaybackQueued().get()) {
            return color.getBlink();
         } else if (slot.isStopQueued().get()) {
            return color.getBlink(); //RgbState.flash(color, 1);
         } else if (slot.isPlaying().get() && track.isQueuedForStop().get()) {
            return RgbColor.GREEN.getBlink();
         } else if (slot.isPlaying().get()) {
            if (track.arm().get() && overdubEnabled) {
               return RgbColor.RED.getBlink();
            }
            return RgbColor.GREEN;
         }
         return color;
      }

      if (slot.isRecordingQueued().get()) {
         return RgbColor.RED.getBlink();
      } else if (track.arm().get()) {
         return RgbColor.RED;
      } else if (slot.isStopQueued().get()) {
         return RgbColor.WHITE.getBlink();
      }

      return RgbColor.OFF;
   }

   @Activate
   public void onActivate() {
      DebugOutOxy.println("ACTIVATE SESSION LAYER");
      mainLayer.setIsActive(true);
   }

   private void prepareSlot(final ClipLauncherSlot slot, final int buttonIndex) {
      slot.hasContent().markInterested();
      slot.isPlaying().markInterested();
      slot.isStopQueued().markInterested();
      slot.isRecordingQueued().markInterested();
      slot.isRecording().markInterested();
      slot.isPlaybackQueued().markInterested();
      slot.color().addValueObserver((r, g, b) -> {
         slotColors[buttonIndex] = RgbColor.toColor(r, g, b);
      });
   }

}
