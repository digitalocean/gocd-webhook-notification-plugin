package com.digitalocean.gocd.webhook;

import com.digitalocean.gocd.webhook.executors.GetPluginConfigurationExecutor;
import com.digitalocean.gocd.webhook.executors.GetViewRequestExecutor;
import com.digitalocean.gocd.webhook.executors.NotificationInterestedInExecutor;
import com.digitalocean.gocd.webhook.executors.ValidateConfigurationExecutor;
import com.digitalocean.gocd.webhook.executors.WebhookRequestExecutor;
import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.exceptions.UnhandledRequestTypeException;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import com.thoughtworks.go.plugin.api.logging.Logger;

import java.util.Timer;
import java.util.TimerTask;

import static com.digitalocean.gocd.webhook.Constants.PLUGIN_IDENTIFIER;

@Extension
public class WebhookPlugin implements GoPlugin {

    private static final Logger LOGGER = Logger.getLoggerFor(WebhookPlugin.class);

    @SuppressWarnings("FieldCanBeLocal")
    private final Timer timer = new Timer();

    public WebhookPlugin() {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    Configuration.refresh();
                } catch (Exception e) {
                    LOGGER.error("Failed to refresh configuration", e);
                }
            }
        }, 0, 10000); // refresh now, and every 10 seconds thereafter
    }

    @Override
    public void initializeGoApplicationAccessor(GoApplicationAccessor accessor) {
        // ignored
    }

    @Override
    public GoPluginApiResponse handle(GoPluginApiRequest request) throws UnhandledRequestTypeException {
        try {
            return executorForRequest(request).execute();
        } catch (UnhandledRequestTypeException e) {
            throw e; // no need to wrap in a runtime exception
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public GoPluginIdentifier pluginIdentifier() {
        return PLUGIN_IDENTIFIER;
    }

    private RequestExecutor executorForRequest(GoPluginApiRequest request) throws Exception {
        switch (PluginRequest.fromString(request.requestName())) {
            case PLUGIN_SETTINGS_GET_VIEW:
                return new GetViewRequestExecutor();
            case REQUEST_NOTIFICATIONS_INTERESTED_IN:
                return new NotificationInterestedInExecutor();
            case REQUEST_STAGE_STATUS:
                return getStageStatusWebhook(request);
            case REQUEST_AGENT_STATUS:
                return getAgentStatusWebhook(request);
            case PLUGIN_SETTINGS_GET_CONFIGURATION:
                return new GetPluginConfigurationExecutor();
            case PLUGIN_SETTINGS_VALIDATE_CONFIGURATION:
                return new ValidateConfigurationExecutor();
            default:
                throw new UnhandledRequestTypeException(request.requestName());
        }
    }

    private RequestExecutor getStageStatusWebhook(GoPluginApiRequest request) {
        Configuration config = Configuration.current();
        return new WebhookRequestExecutor(config.getClient(), config.getStageEndpoints(), request.requestBody());
    }

    private RequestExecutor getAgentStatusWebhook(GoPluginApiRequest request) {
        Configuration config = Configuration.current();
        return new WebhookRequestExecutor(config.getClient(), config.getAgentEndpoints(), request.requestBody());
    }
}
