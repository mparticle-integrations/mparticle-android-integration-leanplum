package com.mparticle.kits;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.leanplum.Leanplum;
import com.leanplum.LeanplumActivityHelper;
import com.leanplum.LeanplumDeviceIdMode;
import com.leanplum.LeanplumPushListenerService;
import com.leanplum.LeanplumPushService;
import com.mparticle.MPEvent;
import com.mparticle.MParticle;
import com.mparticle.commerce.CommerceEvent;
import com.mparticle.commerce.Product;
import com.mparticle.internal.ConfigManager;
import com.mparticle.internal.Logger;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


public class LeanplumKit extends KitIntegration implements KitIntegration.PushListener, KitIntegration.AttributeListener, KitIntegration.EventListener, KitIntegration.CommerceListener {
    private final static String APP_ID_KEY = "appId";
    private final static String CLIENT_KEY_KEY = "clientKey";
    private final static String USER_ID_FIELD_KEY = "userIdField";
    private final static String USER_ID_CUSTOMER_ID_VALUE = "customerId";

    @Override
    protected List<ReportingMessage> onKitCreate(Map<String, String> settings, Context context) {
        if (MParticle.isAndroidIdDisabled()) {
            Leanplum.setDeviceIdMode(LeanplumDeviceIdMode.ADVERTISING_ID);
        }
        if (MParticle.getInstance().getEnvironment().equals(MParticle.Environment.Development)) {
            Leanplum.enableVerboseLoggingInDevelopmentMode();
            Leanplum.setAppIdForDevelopmentMode(settings.get(APP_ID_KEY), settings.get(CLIENT_KEY_KEY));
        } else {
            Leanplum.setAppIdForProductionMode(settings.get(APP_ID_KEY), settings.get(CLIENT_KEY_KEY));
        }

        Map<String, Object> attributes = getAllUserAttributes();
        Map<MParticle.IdentityType, String> identities = getUserIdentities();
        String userId;
        if (USER_ID_CUSTOMER_ID_VALUE.equalsIgnoreCase(settings.get(USER_ID_FIELD_KEY))) {
            userId = identities.get(MParticle.IdentityType.CustomerId);
        }else {
            userId = identities.get(MParticle.IdentityType.Email);
        }
        if (!TextUtils.isEmpty(userId)) {
            if (attributes.size() > 0) {
                Leanplum.start(context, userId, attributes);
            } else {
                Leanplum.start(context, attributes);
            }
        }else if (attributes.size() > 0) {
            Leanplum.start(context, attributes);
        }else {
            Leanplum.start(context);
        }

        LeanplumActivityHelper.enableLifecycleCallbacks((Application) context.getApplicationContext());

        List<ReportingMessage> messageList = new LinkedList<ReportingMessage>();
        messageList.add(
                new ReportingMessage(this, ReportingMessage.MessageType.APP_STATE_TRANSITION, System.currentTimeMillis(), null)
        );
        return messageList;
    }

    @Override
    public String getName() {
        return "Leanplum";
    }

    @Override
    public List<ReportingMessage> setOptOut(boolean optedOut) {
        //Leanplum doesn't have the notion of opt-out.
        return null;
    }

    @Override
    public boolean willHandlePushMessage(Intent intent) {
        return intent.getExtras().containsKey("lp_version");
    }

    @Override
    public void onPushMessageReceived(Context context, Intent intent) {
        Intent service = new Intent(context, LeanplumPushListenerService.class);
        service.setAction("com.google.android.c2dm.intent.RECEIVE");
        service.putExtras(intent);
        context.startService(service);
    }

    @Override
    public boolean onPushRegistration(String instanceId, String senderId) {
        LeanplumPushService.setGcmSenderId(senderId);
        LeanplumPushService.setGcmRegistrationId(instanceId);
        return true;
    }

    @Override
    public void setUserAttribute(String key, String value) {
        Map<String, Object> attributes = new HashMap<String, Object>(1);
        attributes.put(key, value);
        Leanplum.setUserAttributes(attributes);
    }

    @Override
    public void setUserAttributeList(String key, List<String> list) {
        Map<String, Object> attributes = new HashMap<String, Object>(1);
        attributes.put(key, list);
        Leanplum.setUserAttributes(attributes);
    }

    @Override
    public boolean supportsAttributeLists() {
        return true;
    }

    @Override
    public void setAllUserAttributes(Map<String, String> attributes, Map<String, List<String>> attributeLists) {
        //we set user attributes on start so there's no point in doing it here as well.
    }

    @Override
    public void removeUserAttribute(String key) {
        Map<String, Object> attribute = new HashMap<String, Object>(1);
        attribute.put(key, null);
        Leanplum.setUserAttributes(attribute);
    }

    @Override
    public void setUserIdentity(MParticle.IdentityType identityType, String id) {
        if (identityType.equals(MParticle.IdentityType.CustomerId) &&
                USER_ID_CUSTOMER_ID_VALUE.equalsIgnoreCase(getSettings().get(USER_ID_FIELD_KEY))) {
            Leanplum.setUserId(id);
        }else if (identityType.equals(MParticle.IdentityType.Email) &&
                !USER_ID_CUSTOMER_ID_VALUE.equalsIgnoreCase(getSettings().get(USER_ID_FIELD_KEY))) {
            Leanplum.setUserId(id);
        }
    }

    @Override
    public void removeUserIdentity(MParticle.IdentityType identityType) {
        if (identityType.equals(MParticle.IdentityType.CustomerId) &&
                USER_ID_CUSTOMER_ID_VALUE.equalsIgnoreCase(getSettings().get(USER_ID_FIELD_KEY))) {
            Leanplum.setUserId(null);
        }else if (identityType.equals(MParticle.IdentityType.Email) &&
                !USER_ID_CUSTOMER_ID_VALUE.equalsIgnoreCase(getSettings().get(USER_ID_FIELD_KEY))) {
            Leanplum.setUserId(null);
        }
    }

    @Override
    public List<ReportingMessage> logout() {
        return null;
    }

    @Override
    public List<ReportingMessage> leaveBreadcrumb(String s) {
        return null;
    }

    @Override
    public List<ReportingMessage> logError(String s, Map<String, String> map) {
        return null;
    }

    @Override
    public List<ReportingMessage> logException(Exception e, Map<String, String> map, String s) {
        return null;
    }

    @Override
    public List<ReportingMessage> logEvent(MPEvent mpEvent) {
        Leanplum.track(mpEvent.getEventName(), mpEvent.getInfo());
        return Arrays.asList(ReportingMessage.fromEvent(this, mpEvent));
    }

    @Override
    public List<ReportingMessage> logScreen(String screenName, Map<String, String> attributes) {
        Leanplum.advanceTo(screenName, attributes);
        return Arrays.asList(new ReportingMessage(this, ReportingMessage.MessageType.SCREEN_VIEW, System.currentTimeMillis(), attributes));
    }

    @Override
    public List<ReportingMessage> logLtvIncrease(BigDecimal valueIncreased, BigDecimal total, String eventName, Map<String, String> attributes) {
        Leanplum.track(eventName, valueIncreased.doubleValue(), attributes);
        List<ReportingMessage> messageList = new LinkedList<ReportingMessage>();
        messageList.add(ReportingMessage.fromEvent(this, new MPEvent.Builder(eventName, MParticle.EventType.Transaction).info(attributes).build()));
        return messageList;
    }

    private void logTransaction(CommerceEvent event, Product product) {
        Map<String, String> eventAttributes = new HashMap<String, String>();
        CommerceEventUtils.extractActionAttributes(event, eventAttributes);
        Leanplum.track(Leanplum.PURCHASE_EVENT_NAME, product.getTotalAmount(),product.getName(), eventAttributes);
    }

    @Override
    public List<ReportingMessage> logEvent(CommerceEvent event) {
        List<ReportingMessage> messages = new LinkedList<ReportingMessage>();
        if (!KitUtils.isEmpty(event.getProductAction()) &&
                event.getProductAction().equalsIgnoreCase(Product.PURCHASE) &&
                event.getProducts().size() > 0) {
            List<Product> productList = event.getProducts();
            for (Product product : productList) {
                logTransaction(event, product);
            }
            messages.add(ReportingMessage.fromEvent(this, event));
            return messages;
        }
        List<MPEvent> eventList = CommerceEventUtils.expand(event);
        if (eventList != null) {
            for (int i = 0; i < eventList.size(); i++) {
                try {
                    logEvent(eventList.get(i));
                    messages.add(ReportingMessage.fromEvent(this, event));
                } catch (Exception e) {
                    Logger.warning("Failed to call track to Leanplum kit: " + e.toString());
                }
            }
        }
        return messages;
    }
}
