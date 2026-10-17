package com.renanwillian.easyproxy;

import picocli.CommandLine.IVersionProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class VersionProvider implements IVersionProvider {

    static final String RESOURCE = "/easy-proxy-version.properties";
    static final String UNKNOWN = "unknown";

    @Override
    public String[] getVersion() throws IOException {
        return new String[]{readVersion(RESOURCE)};
    }

    static String readVersion(String resource) throws IOException {
        try (InputStream in = VersionProvider.class.getResourceAsStream(resource)) {
            if (in == null) return UNKNOWN;
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("version", UNKNOWN);
        }
    }
}
