package com.bitwig.extensions.controllers.akai.apc40_mkii;

import java.util.Objects;

import com.bitwig.extension.api.Color;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ClipLauncherSlot;
import com.bitwig.extension.controller.api.ClipLauncherSlotBank;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.DetailEditor;
import com.bitwig.extension.controller.api.HardwareButton;
import com.bitwig.extension.controller.api.MasterRecorder;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extensions.framework.Layer;


class MeliAPC40MKIIControllerExtension extends APC40MKIIControllerExtension
{
   private static final int ZOOM_TO_FIT_TIMEOUT = 100;
   private static final int BT_FOOTSWITCH = 64;
   private static final String SUB_PANEL_LAYOUT_DEVICE = "DEVICE";
   private static final String SUB_PANEL_LAYOUT_DETAIL = "DETAIL";
   private static final String SUB_PANEL_LAYOUT_AUTOMATION = "AUTOMATION";

   private ControllerHost host;
   private Layer mPanSelectLayer;
   private DetailEditor mDetailEditor;
   private MasterRecorder mMasterRecorder;
   private HardwareButton footswitchButton;

   private final DoublePressedButtonState mPanOn = new DoublePressedButtonState();
   private String selectedSubPanel = SUB_PANEL_LAYOUT_DEVICE;


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
         if (mDeviceCursor.isPinned().get() || mTrackCursor.isPinned().get()) {
            mDeviceCursor.isPinned().set(false);
            mTrackCursor.isPinned().set(false);
         } else {
            mDeviceCursor.isPinned().set(true);
            mTrackCursor.isPinned().set(true);
         }
      }, () -> "Toggle device & track pin"));
      mMainLayer.bind(() -> mDeviceCursor.isPinned().get() && mTrackCursor.isPinned().get(), mDeviceLockLed);

      // BUTTON 7
      mMainLayer.bindPressed(mClipDeviceViewButton, getHost().createAction(() -> {
         if (Objects.equals(selectedSubPanel, SUB_PANEL_LAYOUT_DEVICE)) {
            mApplication.getAction("Select sub panel 1").invoke();
            selectedSubPanel = SUB_PANEL_LAYOUT_DETAIL;
         } else if (Objects.equals(selectedSubPanel, SUB_PANEL_LAYOUT_DETAIL)) {
            mApplication.getAction("Select sub panel 2").invoke();
            selectedSubPanel = SUB_PANEL_LAYOUT_AUTOMATION;
         } else if (Objects.equals(selectedSubPanel, SUB_PANEL_LAYOUT_AUTOMATION)) {
            mApplication.getAction("Select sub panel 3").invoke();
            selectedSubPanel = SUB_PANEL_LAYOUT_DEVICE;
         }
         host.scheduleTask(() -> mDetailEditor.zoomToFit(), ZOOM_TO_FIT_TIMEOUT);
      }, () -> "Next Sub Panel"));
      mMainLayer.bind(() -> selectedSubPanel.equals(SUB_PANEL_LAYOUT_DEVICE), mClipDeviceViewLed);

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

      // BUTTON 1
      mShiftLayer.bindPressed(mPrevDeviceButton, () -> {
         activateDeviceControlsMode(DeviceControlMode.TRACK_CONTROLS);
      });
      mShiftLayer.bind(() -> mDeviceControlMode == DeviceControlMode.TRACK_CONTROLS, mPrevDeviceLed);

      // BUTTON 2
      mShiftLayer.bindPressed(mNextDeviceButton, () -> {
         activateDeviceControlsMode(DeviceControlMode.DEVICE_CONTROLS);
      });
      mShiftLayer.bind(() -> mDeviceControlMode == DeviceControlMode.DEVICE_CONTROLS, mNextDeviceLed);

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

               mApplication.getAction("Select sub panel 1").invoke();
               selectedSubPanel = SUB_PANEL_LAYOUT_DETAIL;

               host.scheduleTask(() -> mDetailEditor.zoomToFit(), ZOOM_TO_FIT_TIMEOUT);
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

   @Override
   protected void createHardwareControls()
   {
      super.createHardwareControls();

      updateHardwareControls();
   }

   private void updateHardwareControls()
   {
      footswitchButton = mHardwareSurface.createHardwareButton("Footswitch");
      footswitchButton.setLabel("FS");
      final int valueWhenPressed = 127;
      footswitchButton.pressedAction().setActionMatcher(mMidiIn.createCCActionMatcher(0, BT_FOOTSWITCH, valueWhenPressed));
   }
}
