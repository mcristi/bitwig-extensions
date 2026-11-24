package com.bitwig.extensions.controllers.akai.apc40_mkii;

import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.Action;
import com.bitwig.extension.controller.api.Arranger;
import com.bitwig.extension.controller.api.ClipLauncherSlot;
import com.bitwig.extension.controller.api.ClipLauncherSlotBank;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.DetailEditor;
import com.bitwig.extension.controller.api.MasterRecorder;
import com.bitwig.extension.controller.api.RemoteControl;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extensions.CommonState;
import com.bitwig.extensions.Globals;
import com.bitwig.extensions.framework.Layer;
import com.bitwig.extensions.util.ColorUtils;
import static com.bitwig.extension.controller.api.Application.PANEL_LAYOUT_EDIT;
import static com.bitwig.extension.controller.api.Application.PANEL_LAYOUT_MIX;


class MeliAPC40MKIIControllerExtension extends APC40MKIIControllerExtension
{
   private ControllerHost host;
   private Layer mPanSelectLayer;
   private DetailEditor mDetailEditor;
   private MasterRecorder mMasterRecorder;
   private Arranger mArranger;
   private Action mTogglePlaybackFollowAction;

   private final DoublePressedButtonState mPanOn = new DoublePressedButtonState();

   // Mapping mode of USER + track select, see toggleRemoteLearn: the project remote control (= top
   // knob) that is being mapped (-1 = the mode is off) and whether it already waits for a parameter
   // (it does not yet while a new page is being created for it)
   private static final int MAPPING_ATTEMPTS = 10;
   private static final long MAPPING_RETRY_MS = 400;
   private static final long LEARN_FLASH_MS = 400;
   private int mLearnKnob = -1;
   private boolean mLearnWaiting = false;
   private int mLearnId = 0;
   private final boolean[] mDeviceRemoteTouched = new boolean[8];

   protected MeliAPC40MKIIControllerExtension(
      final ControllerExtensionDefinition controllerExtensionDefinition,
      final ControllerHost host)
   {
      super(controllerExtensionDefinition, host);
   }

   @Override
   public void init()
   {
      host = getHost();

      mMasterRecorder = host.createMasterRecorder();
      mDetailEditor = host.createDetailEditor();
      mArranger = host.createArranger();

      super.init();

      mTrackCursor.isPinned().markInterested();
      mDeviceCursor.isPlugin().markInterested();
      mArranger.isPlaybackFollowEnabled().markInterested();
      mApplication.panelLayout().markInterested();

      // The API has no playback-follow for the detail (clip) editor, so use
      // Bitwig's global action, which applies to the focused panel.
      mTogglePlaybackFollowAction = mApplication.getAction("toggle_playhead_follow");

      // Bitwig's mapping mode takes the parameter that is "touched". A plug-in reports that itself,
      // but a remote control of a Bitwig device that is merely set from a controller does not count.
      // So while a mapping waits, turning a device control knob also touches its remote control.
      for (int i = 0; i < 8; ++i)
      {
         final int knob = i;
         mDeviceControlKnobs[i].value().addValueObserver(value -> {
            if (mLearnWaiting)
               touchDeviceRemote(knob);
         });
      }

      mProjectRemoteControls.pageNames().markInterested();
      // another page: the control that waits for a parameter is not the one on the knob anymore
      mProjectRemoteControls.selectedPageIndex().addValueObserver(page -> {
         if (mLearnWaiting)
         {
            cancelRemoteLearn();
            host.showPopupNotification("Mapping cancelled");
         }
      });
      for (int i = 0; i < 8; ++i)
      {
         final int knob = i;
         final RemoteControl control = mProjectRemoteControls.getParameter(i);
         control.isBeingMapped().markInterested();
         control.name().markInterested();
         // an empty control does not exist: it does as soon as it is mapped
         control.exists().addValueObserver(mapped -> {
            if (mapped)
               remoteLearned(knob);
         });
      }
   }

   @Override
   protected void createLayers()
   {
      super.createLayers();

      mPanSelectLayer = new Layer(mLayers, "Pan Select");

      updateMainLayer();
      mMainLayer.activate();

      updateShiftLayer();
      updateSendSelectLayer();
      updateProjectRemoteSelectLayer();
      updateBankLayer();

      createPanSelectLayer();
      updateTopControls();
   }

   private void updateMainLayer()
   {
      mMainLayer.bindPressed(mPlayButton, () -> { mTransport.resetAutomationOverrides();});
      mMainLayer.bind(mTransport.isAutomationOverrideActive(), mPlayLed);

      mMainLayer.bindToggle(mRecordButton, mTransport.isArrangerRecordEnabled());

      mMainLayer.bindPressed(mSessionButton, () -> { mMasterRecorder.toggle(); });
      mMainLayer.bind(mMasterRecorder.isActive(), mSessionLed);

      mMainLayer.bindPressed(nudgeMinusButton, () -> {
         mApplication.undo();
      });
      mMainLayer.bindPressed(nudgePlusButton, () -> {
         mApplication.redo();
      });

      // BUTTON 6
      mMainLayer.bindPressed(mDeviceLockButton, getHost().createAction(() -> {
         if (mDeviceCursor.isPinned().get() && mPinnableTrackCursor.isPinned().get()) {
            mDeviceCursor.isPinned().set(false);
            mPinnableTrackCursor.isPinned().set(false);
         } else {
            mDeviceCursor.isPinned().set(true);
            mPinnableTrackCursor.isPinned().set(true);
         }
      }, () -> "Toggle device & track pin"));
      mMainLayer.bind(() -> mDeviceCursor.isPinned().get() && mPinnableTrackCursor.isPinned().get(), mDeviceLockLed);

      // BUTTON 7
      mMainLayer.bindPressed(mClipDeviceViewButton, getHost().createAction(() -> {
         if (mApplication.panelLayout().get().equals(PANEL_LAYOUT_MIX)) {
            mApplication.setPanelLayout(PANEL_LAYOUT_EDIT);
         } else {
            mApplication.setPanelLayout(PANEL_LAYOUT_MIX);
         }
         host.scheduleTask(() -> mDetailEditor.zoomToFitSelectionOrAll(), Globals.VISUAL_FEEDBACK_TIMEOUT);
      }, () -> "Next Sub Panel"));
      mMainLayer.bind(() -> mApplication.panelLayout().get().equals(PANEL_LAYOUT_EDIT), mClipDeviceViewLed);

      // BUTTON 8
      mMainLayer.bindPressed(mDetailViewButton, () -> {
         if (mDeviceCursor.isPlugin().get())
            mDeviceCursor.isWindowOpen().toggle();
         else
            mDeviceCursor.isExpanded().toggle();
      });
   }

   private void updateShiftLayer()
   {
      mShiftLayer.bind(mCueLevelKnob, mProject.cueMix());

      mShiftLayer.bindPressed(nudgeMinusButton, () -> {
         mApplication.navigateToParentTrackGroup();
      });
      mShiftLayer.bindPressed(nudgePlusButton, () -> {
         if (mTrackCursor.isGroup().get())
            mApplication.navigateIntoTrackGroup(mTrackCursor);
      });

      // button 6
      mShiftLayer.bindPressed(mDeviceLockButton, () -> {
         if (mTogglePlaybackFollowAction != null)
            mTogglePlaybackFollowAction.invoke();
         else
            mArranger.isPlaybackFollowEnabled().toggle();
      });

      // button 7
      mShiftLayer.bindPressed(mClipDeviceViewButton, () -> {
         mApplication.nextPanelLayout();
      });

      // button 8
      mShiftLayer.bindPressed(mDetailViewButton, () -> {
         mDeviceCursor.isRemoteControlsSectionVisible().toggle();
      });
   }

   private void updateProjectRemoteSelectLayer()
   {
      // USER + track select: mapping mode for that project remote control. The select LEDs are off
      // while USER is held, as the buttons do not select tracks then; the one of the control that
      // is being mapped flashes along with its knob.
      for (int i = 0; i < 8; ++i)
      {
         final int knob = i;
         mProjectSelectLayer.bindPressed(mTrackSelectButtons[i], () -> toggleRemoteLearn(knob));
         mProjectSelectLayer.bind(() -> knob == mLearnKnob && isLearnFlashOn(), mTrackSelectLeds[i]);
      }

      for (int i = 0; i < 8; ++i)
      {
         final Track track = mTrackBank.getItemAt(i);
         final ClipLauncherSlotBank clipLauncherSlotBank = track.clipLauncherSlotBank();
         for (int j = 0; j < 5; ++j)
         {
            final ClipLauncherSlot slot = clipLauncherSlotBank.getItemAt(j);
            mProjectSelectLayer.bindPressed(mGridButtons[i + 8 * j], slot::deleteObject);
         }
      }
   }

   private void updateSendSelectLayer()
   {
      for (int i = 0; i < 8; ++i)
      {
         final Track track = mTrackBank.getItemAt(i);
         final ClipLauncherSlotBank clipLauncherSlotBank = track.clipLauncherSlotBank();
         for (int j = 0; j < 5; ++j)
         {
            final ClipLauncherSlot slot = clipLauncherSlotBank.getItemAt(j);
            mSendSelectLayer.bindPressed(mGridButtons[i + 8 * j], slot::duplicateClip);
         }
      }
   }

   private void updateBankLayer()
   {
      // cycle to colors and set color to clip
      for (int i = 0; i < 8; ++i)
      {
         final Track track = mTrackBank.getItemAt(i);
         final ClipLauncherSlotBank clipLauncherSlotBank = track.clipLauncherSlotBank();
         for (int j = 0; j < 5; ++j)
         {
            final ClipLauncherSlot slot = clipLauncherSlotBank.getItemAt(j);
            mBankLayer.bindPressed(mGridButtons[i + 8 * j], () -> slot.color().set(ColorUtils.getColor(slot)));
         }
      }
   }

   private void createPanSelectLayer()
   {
      // select clip and zoom to fit
      for (int i = 0; i < 8; ++i)
      {
         final Track track = mTrackBank.getItemAt(i);
         final ClipLauncherSlotBank clipLauncherSlotBank = track.clipLauncherSlotBank();
         for (int j = 0; j < 5; ++j)
         {
            final ClipLauncherSlot slot = clipLauncherSlotBank.getItemAt(j);
            mPanSelectLayer.bindPressed(mGridButtons[i + 8 * j], () -> {
               mApplication.setPanelLayout(PANEL_LAYOUT_EDIT);
               slot.select();

               host.scheduleTask(() -> mDetailEditor.zoomToFitSelectionOrAll(), Globals.VISUAL_FEEDBACK_TIMEOUT);
            });
         }
      }
   }

   private void updateTopControls()
   {
      // The mapping mode only lasts while USER is held; PAN or SENDS (other knob modes) end it too
      mUserButton.isPressed().addValueObserver(isPressed -> {
         if (!isPressed)
            cancelRemoteLearnWithMessage();
      });
      mSendsButton.isPressed().addValueObserver(isPressed -> {
         if (isPressed)
            cancelRemoteLearnWithMessage();
      });

      mPanButton.isPressed().addValueObserver(isPressed -> {
         if (isPressed)
            cancelRemoteLearnWithMessage();
         mPanOn.stateChanged(isPressed);
         if (!isPressed)
            mPanOn.clear(); // hold only, no latch on double press
         if (mPanOn.isOn())
            mPanSelectLayer.activate();
         else
            mPanSelectLayer.deactivate();
      });
   }

   /**
    * USER + track select: mapping mode for the project remote control on that top knob. The knob's
    * LED ring flashes for as long as USER stays held, and the next parameter touched in Bitwig, by mouse, from another controller or
    * with a device control knob of the APC40, is mapped to it; the ring then shows its value. If the
    * control is mapped already, a new page of project remote controls is created, the knobs switch
    * to it, and the control is mapped there; the old page stays as it is. Releasing USER
    * cancels, as does USER + the same select button again; USER + another one moves the mapping
    * mode to that knob.
    */
   private void toggleRemoteLearn(final int knob)
   {
      final boolean wasThisKnob = mLearnKnob == knob;
      if (mLearnKnob != -1)
         cancelRemoteLearn();
      if (wasThisKnob)
      {
         host.showPopupNotification("Mapping cancelled");
         return;
      }

      final int id = ++mLearnId;
      mLearnKnob = knob;
      setLearnWaiting(false);
      learnFlashTick(id);

      final boolean mapped = mProjectRemoteControls.getParameter(knob).exists().get();
      if (mapped || mProjectRemoteControls.pageCount().get() == 0)
      {
         // Mapped already (or there is no page at all yet). Bitwig lets a script neither delete a
         // mapped control nor put it into mapping mode, so mapping again means a new page: create
         // one, switch to it, map on that.
         if (mapped)
            host.showPopupNotification("Project remote " + (knob + 1) + " is mapped: new page");
         final String[] pagesBefore = mProjectRemoteControls.pageNames().get();
         mProjectRemoteControls.createPresetPage();
         mapOnNewPage(id, knob, pagesBefore, 20);
         return;
      }

      host.showPopupNotification("Mapping project remote " + (knob + 1) + ": touch a parameter");
      awaitParameter(id, knob, MAPPING_ATTEMPTS);
   }

   /**
    * Waits for the page that was just created to show up, switches the knobs to it and starts
    * mapping the control there.
    *
    * @param pagesBefore the names of the pages before the new one was created. The new page is not
    *                    necessarily the last, so it is found as the first place where the names differ.
    * @param attempts    how many more times to look, 100 ms apart
    */
   private void mapOnNewPage(final int id, final int knob, final String[] pagesBefore, final int attempts)
   {
      host.scheduleTask(() -> {
         if (id != mLearnId)
            return;
         final String[] pages = mProjectRemoteControls.pageNames().get();
         if (pages.length > pagesBefore.length)
         {
            int newPage = 0;
            while (newPage < pagesBefore.length && pages[newPage].equals(pagesBefore[newPage]))
               newPage++;
            mProjectRemoteControls.selectedPageIndex().set(newPage);
            // and a moment for the controls to be those of the new page
            host.scheduleTask(() -> {
               if (id == mLearnId)
               {
                  host.showPopupNotification("Mapping project remote " + (knob + 1) + ": touch a parameter");
                  awaitParameter(id, knob, MAPPING_ATTEMPTS);
               }
            }, 200);
         }
         else if (attempts > 0)
         {
            mapOnNewPage(id, knob, pagesBefore, attempts - 1);
         }
         else
         {
            cancelRemoteLearn();
            host.showPopupNotification("Mapping: no new page");
         }
      }, 100);
   }

   /**
    * Puts the control into Bitwig's mapping mode, where it waits for a parameter to be touched.
    * Bitwig does not always take this at once, for instance right after the page was switched, so
    * it is checked and asked again until the control really is waiting.
    *
    * @param attempts how many more times to ask, MAPPING_RETRY_MS apart
    */
   private void awaitParameter(final int id, final int knob, final int attempts)
   {
      if (id != mLearnId)
         return;
      final RemoteControl control = mProjectRemoteControls.getParameter(knob);
      setLearnWaiting(true);
      control.isBeingMapped().set(true);
      host.scheduleTask(() -> {
         // still this mapping, not mapped in the meantime, and Bitwig is not waiting
         if (id == mLearnId && !control.exists().get() && !control.isBeingMapped().get())
         {
            if (attempts > 0)
            {
               awaitParameter(id, knob, attempts - 1);
            }
            else
            {
               cancelRemoteLearn();
               host.showPopupNotification("Mapping: could not start");
            }
         }
      }, MAPPING_RETRY_MS);
   }

   private void setLearnWaiting(final boolean waiting)
   {
      mLearnWaiting = waiting;
      // the other controllers of this extension touch the remote controls their knobs turn, too
      CommonState.getInstance().setRemoteMappingWaiting(waiting);
   }

   @Override
   public void exit()
   {
      CommonState.getInstance().setRemoteMappingWaiting(false);
      super.exit();
   }

   private void touchDeviceRemote(final int knob)
   {
      if (mDeviceRemoteTouched[knob])
         return;
      mDeviceRemoteTouched[knob] = true;
      final RemoteControl control = mDeviceRemoteControls.getParameter(knob);
      control.touch(true);
      host.scheduleTask(() -> {
         control.touch(false);
         mDeviceRemoteTouched[knob] = false;
      }, 200);
   }

   private void remoteLearned(final int knob)
   {
      if (knob != mLearnKnob || !mLearnWaiting)
         return;
      endRemoteLearn();
      // a moment later the name of the new mapping is known
      host.scheduleTask(() -> host.showPopupNotification(
         "Project remote " + (knob + 1) + ": " + mProjectRemoteControls.getParameter(knob).name().get()), 150);
   }

   private void cancelRemoteLearnWithMessage()
   {
      if (mLearnKnob != -1)
      {
         cancelRemoteLearn();
         host.showPopupNotification("Mapping cancelled");
      }
   }

   private void cancelRemoteLearn()
   {
      if (mLearnKnob != -1)
         mProjectRemoteControls.getParameter(mLearnKnob).isBeingMapped().set(false);
      endRemoteLearn();
   }

   private void endRemoteLearn()
   {
      final int knob = mLearnKnob;
      mLearnId++;
      mLearnKnob = -1;
      setLearnWaiting(false);
      // the ring shows the value of the control again
      if (knob != -1)
         updateTopControlRing(knob);
   }

   /** Keeps the LED ring of the knob and its select button flashing for as long as the mapping mode is on. */
   private void learnFlashTick(final int id)
   {
      if (id != mLearnId || mLearnKnob == -1)
         return;
      updateTopControlRing(mLearnKnob);
      host.requestFlush(); // for the select button's LED
      host.scheduleTask(() -> learnFlashTick(id), 50);
   }

   private boolean isLearnFlashOn()
   {
      return System.currentTimeMillis() / LEARN_FLASH_MS % 2 == 0;
   }

   @Override
   protected void overrideTopKnobLed(final int knobIndex, final KnobLed knobLed)
   {
      if (knobIndex != mLearnKnob)
         return;
      // flash the whole ring: an empty control has no value to show
      if (isLearnFlashOn())
      {
         knobLed.set(127);
         knobLed.setRing(KnobLed.RING_VOLUME);
      }
      else
      {
         knobLed.setRing(KnobLed.RING_OFF);
      }
   }
}
