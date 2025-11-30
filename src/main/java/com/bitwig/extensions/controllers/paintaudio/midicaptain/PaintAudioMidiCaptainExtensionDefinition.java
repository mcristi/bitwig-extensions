package com.bitwig.extensions.controllers.paintaudio.midicaptain;
import java.util.UUID;

import com.bitwig.extension.api.PlatformType;
import com.bitwig.extension.controller.AutoDetectionMidiPortNamesList;
import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;

public class PaintAudioMidiCaptainExtensionDefinition extends ControllerExtensionDefinition
{
   private static final UUID ID = UUID.fromString("33757f1c-1043-420a-b7a8-e9cf7ee061d2");

   public PaintAudioMidiCaptainExtensionDefinition()
   {
   }

   @Override
   public String getName()
   {
      return "Midi Captain";
   }

   @Override
   public String getAuthor()
   {
      return "mcristi";
   }

   @Override
   public String getVersion()
   {
      return "1.0";
   }

   @Override
   public UUID getId()
   {
      return ID;
   }

   @Override
   public String getHardwareVendor()
   {
      return "PaintAudio";
   }

   @Override
   public String getHardwareModel()
   {
      return getName();
   }

   @Override
   public int getRequiredAPIVersion()
   {
      return 24;
   }

   @Override
   public int getNumMidiInPorts()
   {
      return 1;
   }

   @Override
   public int getNumMidiOutPorts()
   {
      return 1;
   }

   @Override
   public void listAutoDetectionMidiPortNames(final AutoDetectionMidiPortNamesList list, final PlatformType platformType)
   {
      if (platformType == PlatformType.WINDOWS)
      {
         // Set the correct names of the ports for auto detection on Windows platform here
      }
      else if (platformType == PlatformType.MAC)
      {
          list.add(new String[]{"Scarlett 2i4 USB"}, new String[]{"Scarlett 2i4 USB"});
      }
      else if (platformType == PlatformType.LINUX)
      {
         // Set the correct names of the ports for auto detection on Windows platform here
      }
   }

   @Override
   public ControllerExtension createInstance(final ControllerHost host)
   {
      return new PaintAudioMidiCaptainExtension(this, host);
   }
}
