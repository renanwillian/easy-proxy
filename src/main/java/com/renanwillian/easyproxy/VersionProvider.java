package com.renanwillian.easyproxy;

import picocli.CommandLine.IVersionProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class VersionProvider implements IVersionProvider {

    @Override
    public String[] getVersion() throws IOException {
        try (InputStream in = VersionProvider.class.getResourceAsStream("/easy-proxy-version.properties")) {
            Properties properties = new Properties();
            properties.load(in);
            return new String[] { properties.getProperty("version") };
        }
    }
}
