package com.bitwig.extensions.controllers.akai.apc40_mkii;

import java.util.HashMap;

import com.bitwig.extension.api.Color;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.ClipLauncherSlot;
import com.bitwig.extension.controller.api.ClipLauncherSlotBank;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.DetailEditor;
import com.bitwig.extension.controller.api.HardwareActionBindable;
import com.bitwig.extension.controller.api.HardwareControlType;
import com.bitwig.extension.controller.api.RemoteControl;
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

   protected RemoteControl lastRemoteControl;

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

      mApplication.panelLayout().markInterested();

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
      mMainLayer.bind(mTransport.isAutomationOverrideActive(), mPlayLed);
      mMainLayer.bindPressed(mPlayButton, () -> {
         mTransport.resetAutomationOverrides();
      });

      mMainLayer.bindPressed(nudgeMinusButton, () -> {
         mApplication.undo();
      });
      mMainLayer.bindPressed(nudgePlusButton, () -> {
         mApplication.redo();
      });

      // LED 7
      mMainLayer.bind(() -> mApplication.panelLayout().get().equals(Application.PANEL_LAYOUT_ARRANGE), mClipDeviceViewLed);

      // button 8
      mMainLayer.bindPressed(mDetailViewButton, () -> {
         if (mDeviceCursor.isPlugin().get())
            mDeviceCursor.isWindowOpen().toggle();
         else
            mDeviceCursor.isExpanded().toggle();
      });

      for (int i = 0; i < 8; ++i)
      {
         final int index = i;

         mRemoteControls.getParameter(index).value().addValueObserver(val -> this.lastRemoteControl = mRemoteControls.getParameter(index));

         mMainLayer.bindPressed(mMuteButtons[index], () -> {
            final int trackPos = mTrackBank.getItemAt(index).position().get();
            boolean isTrackRemoteEnabled = mTrackRemoteMap.getOrDefault(trackPos, false);
            mTrackRemoteMap.put(trackPos, !isTrackRemoteEnabled);
            if (isTrackRemoteEnabled) { mTrackLayer.deactivate(); } else { mTrackLayer.activate(); }
         });
         mMainLayer.bind(() -> mTrackRemoteMap.getOrDefault(mTrackBank.getItemAt(index).position().get(), false), mMuteLeds[index]);
      }
   }

   private void updateShiftLayer()
   {
      // TODO: add led status as well
      //      mShiftLayer.bindToggle(mSessionButton, mMasterRecorder.isActive());

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
      final HardwareActionBindable incValAction = getHost().createAction(() -> lastRemoteControl.value().inc(1, 128), () -> "Increments last remote control");
      final HardwareActionBindable decValAction = getHost().createAction(() -> lastRemoteControl.value().inc(-1, 128), () -> "Decrements last remote control");
      mBankLayer.bind(mTempoKnob, getHost().createRelativeHardwareControlStepTarget(incValAction, decValAction));

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

      // button & LED 1
      mTrackLayer.bindPressed(mPrevDeviceButton, () -> {
         if (mTrackRemoteControls.getParameter(0).value().get() == 0)
            mTrackRemoteControls.getParameter(0).value().set(1);
         else
            mTrackRemoteControls.getParameter(0).value().set(0);
      });
      mTrackLayer.bind(() -> mTrackRemoteControls.getParameter(0).value().get() != 0, mPrevDeviceLed);

      // button & LED 2
      mTrackLayer.bindPressed(mNextDeviceButton, () -> {
         if (mTrackRemoteControls.getParameter(1).value().get() == 0)
            mTrackRemoteControls.getParameter(1).value().set(1);
         else
            mTrackRemoteControls.getParameter(1).value().set(0);
      });
      mTrackLayer.bind(() -> mTrackRemoteControls.getParameter(1).value().get() != 0, mNextDeviceLed);

      // button & LED 3
      mTrackLayer.bindPressed(mPrevBankButton, () -> {
         if (mTrackRemoteControls.getParameter(2).value().get() == 0)
            mTrackRemoteControls.getParameter(2).value().set(1);
         else
            mTrackRemoteControls.getParameter(2).value().set(0);
      });
      mTrackLayer.bind(() -> mTrackRemoteControls.getParameter(2).value().get() != 0, mPrevBankLed);

      // button & LED 4
      mTrackLayer.bindPressed(mNextBankButton, () -> {
         if (mTrackRemoteControls.getParameter(3).value().get() == 0)
            mTrackRemoteControls.getParameter(3).value().set(1);
         else
            mTrackRemoteControls.getParameter(3).value().set(0);
      });
      mTrackLayer.bind(() -> mTrackRemoteControls.getParameter(3).value().get() != 0, mNextBankLed);

      // button & LED 5
      mTrackLayer.bindPressed(mDeviceOnOffButton, () -> {
         if (mTrackRemoteControls.getParameter(4).value().get() == 0)
            mTrackRemoteControls.getParameter(4).value().set(1);
         else
            mTrackRemoteControls.getParameter(4).value().set(0);
      });
      mTrackLayer.bind(() -> mTrackRemoteControls.getParameter(4).value().get() != 0, mDeviceOnOffLed);

      // button & LED 6
      mTrackLayer.bindPressed(mDeviceLockButton, () -> {
         if (mTrackRemoteControls.getParameter(5).value().get() == 0)
            mTrackRemoteControls.getParameter(5).value().set(1);
         else
            mTrackRemoteControls.getParameter(5).value().set(0);
      });
      mTrackLayer.bind(() -> mTrackRemoteControls.getParameter(5).value().get() != 0, mDeviceLockLed);

      // button & LED 7
      mTrackLayer.bindPressed(mClipDeviceViewButton, () -> {
         if (mTrackRemoteControls.getParameter(6).value().get() == 0)
            mTrackRemoteControls.getParameter(6).value().set(1);
         else
            mTrackRemoteControls.getParameter(6).value().set(0);
      });
      mTrackLayer.bind(() -> mTrackRemoteControls.getParameter(6).value().get() != 0, mClipDeviceViewLed);

      // button & LED 8
      mTrackLayer.bindPressed(mDetailViewButton, () -> {
         if (mTrackRemoteControls.getParameter(7).value().get() == 0)
            mTrackRemoteControls.getParameter(7).value().set(1);
         else
            mTrackRemoteControls.getParameter(7).value().set(0);
      });
      mTrackLayer.bind(() -> mTrackRemoteControls.getParameter(7).value().get() != 0, mDetailViewLed);

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
            {
               mTrackBankLayer.activate();
               mTrackRemoteControls.selectedPageIndex().set(1);
            }
         }
         else
         {
            mTrackBankLayer.deactivate();
         }
      });
   }

   private void showPopup(String message)
   {
      getHost().showPopupNotification(message);
   }
}
