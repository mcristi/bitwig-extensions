package com.bitwig.extensions.controllers.akai.midimix;

import java.util.UUID;

import com.bitwig.extension.api.PlatformType;
import com.bitwig.extension.controller.AutoDetectionMidiPortNamesList;
import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;

public class MidiMixExtensionDefinition extends ControllerExtensionDefinition
{
   private static final UUID ID = UUID.fromString("a3f4b8c1-7d2e-4f9a-b6e1-8c3d5a2f7e90");

   @Override
   public String getName()
   {
      return "MIDI Mix";
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
      return 25;
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
      if (platformType == PlatformType.MAC)
      {
         list.add(new String[]{"MIDI Mix"}, new String[]{"MIDI Mix"});
      }
      else if (platformType == PlatformType.WINDOWS)
      {
         list.add(new String[]{"MIDI Mix"}, new String[]{"MIDI Mix"});
      }
      else if (platformType == PlatformType.LINUX)
      {
         list.add(new String[]{"MIDI Mix MIDI 1"}, new String[]{"MIDI Mix MIDI 1"});
      }
   }

   @Override
   public ControllerExtension createInstance(final ControllerHost host)
   {
      return new MidiMixExtension(this, host);
   }
}
