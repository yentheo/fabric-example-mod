package net.fabricmc.example.configuration;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream.GetField;
import java.util.ArrayList;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class ConfigurationManager {
    public void saveCredentials(String clientId, String clientSecret) {
        RestreamConfiguration restreamConfiguration = getConfiguration();

        restreamConfiguration.clientId = clientId;
        restreamConfiguration.clientSecret = clientSecret;

        persistConfiguration(restreamConfiguration);
    }

    public RestreamConfiguration getConfiguration() {
        RestreamConfiguration restreamConfiguration = new RestreamConfiguration();
        try {
            ObjectMapper om = new ObjectMapper(new YAMLFactory());
            File file = new File("configuration.yaml");
            if (file.exists()) {
                restreamConfiguration = om.readValue(file, RestreamConfiguration.class);
            }
        } catch (JsonParseException e) {
            e.printStackTrace();
        } catch (JsonMappingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return restreamConfiguration;
    }

    public void saveRefreshToken(String name, String refreshToken) {
        RestreamConfiguration configuration = getConfiguration();
        if (configuration.refreshTokens == null) {
            configuration.refreshTokens = new ArrayList<RefreshTokenEntry>();
        }

        RefreshTokenEntry currentRefreshToken = configuration.refreshTokens.stream().filter(x -> x.name.equals(name))
                .findFirst().orElse(null);
        if (currentRefreshToken == null) {
            RefreshTokenEntry newRefreshToken = new RefreshTokenEntry();
            newRefreshToken.name = name;
            newRefreshToken.refreshToken = refreshToken;
            configuration.refreshTokens.add(newRefreshToken);
        } else {
            currentRefreshToken.refreshToken = refreshToken;
        }
        persistConfiguration(configuration);
    }

    public String getRefreshToken(String name) {
        RestreamConfiguration configuration = getConfiguration();
        return configuration.refreshTokens.stream().filter(x -> x.name.equals(name)).findFirst()
                .map(x -> x.refreshToken).orElse(null);
    }

    private void persistConfiguration(RestreamConfiguration configuration) {
        try {
            ObjectMapper om = new ObjectMapper(new YAMLFactory());
            File file = new File("configuration.yaml");

            om.writeValue(file, configuration);
        } catch (JsonParseException e) {
            e.printStackTrace();
        } catch (JsonMappingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}