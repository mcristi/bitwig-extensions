package com.bitwig.extensions.controllers.akai.lpd8_mkii;

import java.util.UUID;

import com.bitwig.extension.api.PlatformType;
import com.bitwig.extension.controller.AutoDetectionMidiPortNamesList;
import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;

public class LPD8MKIIExtensionDefinition extends ControllerExtensionDefinition
{
   private static final UUID ID = UUID.fromString("2a0e9e28-c611-4d2b-8aed-33946b84d825");

   public LPD8MKIIExtensionDefinition()
   {
   }

   @Override
   public String getName()
   {
      return "LPD8 mkII";
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
      return "Akai";
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
          list.add(new String[]{"MPK mini"}, new String[]{"MPK mini"});
          list.add(new String[]{"MPK mini MIDI 1"}, new String[]{"MPK mini MIDI 1"});
      }
      else if (platformType == PlatformType.LINUX)
      {
         // Set the correct names of the ports for auto detection on Linux platform here
      }
   }

   @Override
   public ControllerExtension createInstance(final ControllerHost host)
   {
      return new LPD8MKIIExtension(this, host);
   }
}
