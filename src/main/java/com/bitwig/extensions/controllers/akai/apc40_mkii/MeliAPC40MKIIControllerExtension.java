package com.bitwig.extensions.controllers.akai.apc40_mkii;

import com.bitwig.extension.api.Color;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ClipLauncherSlot;
import com.bitwig.extension.controller.api.ClipLauncherSlotBank;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.DetailEditor;
import com.bitwig.extension.controller.api.MasterRecorder;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extensions.Globals;
import com.bitwig.extensions.framework.Layer;
import static com.bitwig.extension.controller.api.Application.PANEL_LAYOUT_EDIT;
import static com.bitwig.extension.controller.api.Application.PANEL_LAYOUT_MIX;


class MeliAPC40MKIIControllerExtension extends APC40MKIIControllerExtension
{
   private ControllerHost host;
   private Layer mPanSelectLayer;
   private DetailEditor mDetailEditor;
   private MasterRecorder mMasterRecorder;

   private final DoublePressedButtonState mPanOn = new DoublePressedButtonState();

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

      super.init();

      mTrackCursor.isPinned().markInterested();
      mDeviceCursor.isPlugin().markInterested();

      mApplication.panelLayout().markInterested();
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
         host.scheduleTask(() -> mDetailEditor.zoomToFit(), Globals.VISUAL_FEEDBACK_TIMEOUT);
      }, () -> "Next Sub Panel"));
      mMainLayer.bind(() -> mApplication.panelLayout().get().equals(PANEL_LAYOUT_MIX), mClipDeviceViewLed);

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
            mBankLayer.bindPressed(mGridButtons[i + 8 * j], () -> slot.color().set(getSlotColor(slot)));
         }
      }
   }

   private Color getSlotColor(ClipLauncherSlot slot) {
      Color[] colors = {
         Color.fromHex("#d92e24"), // red
         Color.fromHex("#ff5706"), // orange
         Color.fromHex("#44c8ff"), // dark blue
         Color.fromHex("#0099d9"), // light blue
         Color.fromHex("#009d47"), // dark green
         Color.fromHex("#3ebb62"), // light green
         Color.fromHex("#d99d10"), // yellow
         Color.fromHex("#c9c9c9"), // white
         Color.fromHex("#5761c6"), // dark purple
         Color.fromHex("#bc76f0"), // light purple
      };
      Color currentColor = slot.color().get();
      int colorIndex = 0;
      for (int i = 0; i < colors.length; i++) {
         if (colors[i].toHex().equals(currentColor.toHex())) {
            colorIndex = i + 1;
         }
      }
      if (colorIndex == colors.length) {
         colorIndex = 0;
      }
      return colors[colorIndex];
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
               slot.select();
               slot.showInEditor();

               mApplication.setPanelLayout(PANEL_LAYOUT_EDIT);
               host.scheduleTask(() -> mDetailEditor.zoomToFit(), Globals.VISUAL_FEEDBACK_TIMEOUT);
            });
         }
      }
   }

   private void updateTopControls()
   {
      mPanButton.isPressed().addValueObserver(isPressed -> {
         mPanOn.stateChanged(isPressed);
         if (mPanOn.isOn())
            mPanSelectLayer.activate();
         else
            mPanSelectLayer.deactivate();
      });
   }
}
