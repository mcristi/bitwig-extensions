package com.bitwig.extensions.controllers.akai.apc40_mkii;

import java.util.HashMap;

import com.bitwig.extension.api.Color;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ClipLauncherSlot;
import com.bitwig.extension.controller.api.ClipLauncherSlotBank;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.DetailEditor;
import com.bitwig.extension.controller.api.HardwareControlType;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extensions.framework.Layer;
//import com.bitwig.extension.controller.api.MasterRecorder;


class MeliAPC40MKIIControllerExtension extends APC40MKIIControllerExtension
{
   private DetailEditor mDetailEditor;
   //   private MasterRecorder mMasterRecorder;

   private final DoublePressedButtonState mPanOn = new DoublePressedButtonState();

   private Layer mPanSelectLayer;
   private Layer mTrackLayer;
   private Layer mTrackBankLayer;

   private final HashMap<Integer, Boolean> mTrackRemoteMap = new HashMap<Integer, Boolean>();


   protected MeliAPC40MKIIControllerExtension(
      final ControllerExtensionDefinition controllerExtensionDefinition,
      final ControllerHost host)
   {
      super(controllerExtensionDefinition, host);
   }


   @Override
   public void init()
   {
      super.init();

      final ControllerHost host = getHost();
      //      mMasterRecorder = host.createMasterRecorder();
      mDetailEditor = host.createDetailEditor();

      mChannelStripRemoteControls.setHardwareLayout(HardwareControlType.KNOB, 4);
      mChannelStripRemoteControls.hasNext().markInterested();
      mChannelStripRemoteControls.hasPrevious().markInterested();
      mChannelStripRemoteControls.selectedPageIndex().markInterested();

      mTrackCursor.position().markInterested();
      mDeviceCursor.isPlugin().markInterested();

      for (int i = 0; i < 8; ++i)
      {
         final Track track = mTrackBank.getItemAt(i);
         track.position().markInterested();
      }
   }

   @Override
   protected void createLayers()
   {
      super.createLayers();

      mTrackLayer = new Layer(mLayers, "Track");
      mTrackBankLayer = new Layer(mLayers, "Track Bank");
      mPanSelectLayer = new Layer(mLayers, "Pan Select");

      updateMainLayer();
      mMainLayer.activate();

      updateShiftLayer();
      updateSendSelectLayer();
      updateProjectRemoteSelectLayer();
      updateBankLayer();

      createPanSelectLayer();
      createTrackLayer();
      createTrackBankLayer();

      updateTopControls();
      updateDeviceControls();
   }

   private void updateMainLayer()
   {
      mMainLayer.bind(mTransport.isPlaying(), mPlayLed);

      mMainLayer.bindPressed(nudgeMinusButton, () -> {
         mApplication.undo();
      });
      mMainLayer.bindPressed(nudgePlusButton, () -> {
         mApplication.redo();
      });

      // button & LED 5
      mMainLayer.bindPressed(mDeviceOnOffButton, () -> {
         mTransport.resetAutomationOverrides();
      });
      mMainLayer.bind(mTransport.isAutomationOverrideActive(), mDeviceOnOffLed);

      // button 6
      mMainLayer.bindPressed(mDeviceLockButton, () -> {
         int trackIndex = mTrackCursor.position().getAsInt();
         boolean isTrackRemoteEnabled = mTrackRemoteMap.getOrDefault(trackIndex, false);
         mTrackRemoteMap.put(trackIndex, !isTrackRemoteEnabled);

         if (isTrackRemoteEnabled) { mTrackLayer.deactivate(); } else { mTrackLayer.activate(); }
      });

      // button & LED 7
      mMainLayer.bindToggle(mClipDeviceViewButton, mDeviceCursor.isEnabled());
      mMainLayer.bind(mDeviceCursor.isEnabled(), mClipDeviceViewLed);

      // button & LED 8
      mMainLayer.bindPressed(mDetailViewButton, () -> {
         mApplication.nextSubPanel();
      });
      mMainLayer.bind(mDeviceCursor.isWindowOpen(), mDetailViewLed);
   }

   private void updateShiftLayer()
   {
      //      mShiftLayer.bindToggle(mSessionButton, mMasterRecorder.isActive());

      mShiftLayer.bind(mCueLevelKnob, mProject.cueMix());

      // button 6
      mShiftLayer.bindPressed(mDeviceLockButton, () -> {
         mDeviceCursor.isRemoteControlsSectionVisible().toggle();
      });
      // button 8
      mShiftLayer.bindPressed(mDetailViewButton, () -> {
         if (mDeviceCursor.isPlugin().get())
            mDeviceCursor.isWindowOpen().toggle();
         else
            mDeviceCursor.isExpanded().toggle();;
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
               slot.showInEditor();
               mDetailEditor.zoomToFit();
            });
         }
      }
   }

   private void createTrackBankLayer()
   {
      mTrackBankLayer.bindPressed(mPrevDeviceButton, getHost().createAction(
         () -> mChannelStripRemoteControls.selectedPageIndex().set(0), () -> "Select Remote Controls Page 1"));
      mTrackBankLayer.bindPressed(mNextDeviceButton, getHost().createAction(
         () -> mChannelStripRemoteControls.selectedPageIndex().set(1), () -> "Select Remote Controls Page 2"));
      mTrackBankLayer.bindPressed(mPrevBankButton, getHost().createAction(
         () -> mChannelStripRemoteControls.selectedPageIndex().set(2), () -> "Select Remote Controls Page 3"));
      mTrackBankLayer.bindPressed(mNextBankButton, getHost().createAction(
         () -> mChannelStripRemoteControls.selectedPageIndex().set(3), () -> "Select Remote Controls Page 4"));
      mTrackBankLayer.bindPressed(mDeviceOnOffButton, getHost().createAction(
         () -> mChannelStripRemoteControls.selectedPageIndex().set(4), () -> "Select Remote Controls Page 5"));
      mTrackBankLayer.bindPressed(mDeviceLockButton, getHost().createAction(
         () -> mChannelStripRemoteControls.selectedPageIndex().set(5), () -> "Select Remote Controls Page 6"));
      mTrackBankLayer.bindPressed(mClipDeviceViewButton, getHost().createAction(
         () -> mChannelStripRemoteControls.selectedPageIndex().set(6), () -> "Select Remote Controls Page 7"));
      mTrackBankLayer.bindPressed(mDetailViewButton, getHost().createAction(
         () -> mChannelStripRemoteControls.selectedPageIndex().set(7), () -> "Select Remote Controls Page 8"));
      mTrackBankLayer.bind(() -> mChannelStripRemoteControls.selectedPageIndex().get() == 0, mPrevDeviceLed);
      mTrackBankLayer.bind(() -> mChannelStripRemoteControls.selectedPageIndex().get() == 1, mNextDeviceLed);
      mTrackBankLayer.bind(() -> mChannelStripRemoteControls.selectedPageIndex().get() == 2, mPrevBankLed);
      mTrackBankLayer.bind(() -> mChannelStripRemoteControls.selectedPageIndex().get() == 3, mNextBankLed);
      mTrackBankLayer.bind(() -> mChannelStripRemoteControls.selectedPageIndex().get() == 4, mDeviceOnOffLed);
      mTrackBankLayer.bind(() -> mChannelStripRemoteControls.selectedPageIndex().get() == 5, mDeviceLockLed);
      mTrackBankLayer.bind(() -> mChannelStripRemoteControls.selectedPageIndex().get() == 6, mClipDeviceViewLed);
      mTrackBankLayer.bind(() -> mChannelStripRemoteControls.selectedPageIndex().get() == 7, mDetailViewLed);
   }

   private void createTrackLayer() {
      for (int i = 0; i < 8; ++i)
         mTrackLayer.bind(mDeviceControlKnobs[i], mChannelStripRemoteControls.getParameter(i));

      mTrackLayer.bindPressed(mNextBankButton, mChannelStripRemoteControls.selectNextAction());
      mTrackLayer.bind(mChannelStripRemoteControls.hasNext(), mNextBankLed);

      mTrackLayer.bindPressed(mPrevBankButton, mChannelStripRemoteControls.selectPreviousAction());
      mTrackLayer.bind(mChannelStripRemoteControls.hasPrevious(), mPrevBankLed);

      //      NOTE: leave default behavior for device buttons
      //      mTrackLayer.bindPressed(mPrevDeviceButton, () -> {});
      //      mTrackLayer.bind(() -> false, mPrevDeviceLed);
      //      mTrackLayer.bindPressed(mNextDeviceButton, () -> {});
      //      mTrackLayer.bind(() -> false, mNextDeviceLed);

      mTrackLayer.bind(() -> true, mDeviceLockLed); // LED 6 is always on in this layer

      mTrackCursor.position().addValueObserver(newValue -> {
         if (mTrackRemoteMap.getOrDefault(newValue, false))
            mTrackLayer.activate();
         else
            mTrackLayer.deactivate();
      });
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

   private void updateDeviceControls()
   {
      bankButton.isPressed().addValueObserver((isPressed) -> {
         if (mBankOn.isOn())
         {
            if (mTrackRemoteMap.getOrDefault(mTrackCursor.position().getAsInt(), false))
               mTrackBankLayer.activate();
         }
         else
         {
            mTrackBankLayer.deactivate();
         }
      });
   }
}
