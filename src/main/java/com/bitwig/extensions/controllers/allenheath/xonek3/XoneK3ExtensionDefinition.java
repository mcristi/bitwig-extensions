package com.bitwig.extensions.controllers.allenheath.xonek3;

import java.util.UUID;

import com.bitwig.extension.api.PlatformType;
import com.bitwig.extension.controller.AutoDetectionMidiPortNamesList;
import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;

public class XoneK3ExtensionDefinition extends ControllerExtensionDefinition
{
    private static final UUID ID = UUID.fromString("8e458605-9bd5-4d9e-80c5-184d58aaf9ec");

    @Override
    public String getName()
    {
        return "Xone:K3";
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
        return "Allen & Heath";
    }

    @Override
    public String getHardwareModel()
    {
        return "Xone:K3";
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
    public void listAutoDetectionMidiPortNames(final AutoDetectionMidiPortNamesList list,
        final PlatformType platformType)
    {
        // The same vendor, model and port names as Bitwig's own Xone:K3 script, so that the detected
        // controller offers both extensions in its drop-down (as the Kontrol S Mk3 does).
        if (platformType == PlatformType.WINDOWS || platformType == PlatformType.MAC)
        {
            list.add(new String[] {"XONE:K3"}, new String[] {"XONE:K3"});
            list.add(new String[] {"XONE:K3 Bitwig"}, new String[] {"XONE:K3 Bitwig"});
        }
        else
        {
            list.add(new String[] {"XONE:K3 MIDI 1"}, new String[] {"XONE:K3 MIDI 1"});
            list.add(new String[] {"XONE:K3 MIDI 1 Bitwig"}, new String[] {"XONE:K3 MIDI 1 Bitwig"});
        }
    }

    @Override
    public ControllerExtension createInstance(final ControllerHost host)
    {
        return new XoneK3Extension(this, host);
    }
}
