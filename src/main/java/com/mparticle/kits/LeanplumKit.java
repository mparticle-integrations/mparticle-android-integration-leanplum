package com.mparticle.kits;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.leanplum.Leanplum;
import com.leanplum.LeanplumActivityHelper;
import com.leanplum.LeanplumDeviceIdMode;
import com.leanplum.LeanplumPushService;
import com.mparticle.MPEvent;
import com.mparticle.MParticle;
import com.mparticle.commerce.CommerceEvent;
import com.mparticle.commerce.Product;
import com.mparticle.identity.IdentityStateListener;
import com.mparticle.identity.MParticleUser;
import com.mparticle.internal.Logger;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


public class LeanplumKit extends KitIntegration implements KitIntegration.PushListener, KitIntegration.AttributeListener, KitIntegration.EventListener, KitIntegration.CommerceListener, IdentityStateListener {
    private final static String APP_ID_KEY = "appId";
    private final static String CLIENT_KEY_KEY = "clientKey";
    final static String USER_ID_FIELD_KEY = "userIdField";
    final static String USER_ID_CUSTOMER_ID_VALUE = "customerId";
    final static String USER_ID_EMAIL_VALUE = "email";
    final static String USER_ID_MPID_VALUE = "mpid";
    final static String LEANPLUM_EMAIL_USER_ATTRIBUTE = "email";

    private final static String LEGACY_PUSH_LISTENER_PATH = "com.leanplum.LeanplumPushListenerService";
    private Class legacyPushListener = null;
    /**
     * Enable/disable Firebase. Defaults to false - Firebase will be used.
     */
    private boolean disableFirebase = false;

    @Override
    protected List<ReportingMessage> onKitCreate(Map<String, String> settings, Context context) {
        disableFirebase = isFirebaseDisabled();
        if (MParticle.isAndroidIdDisabled()) {
            Leanplum.setDeviceIdMode(LeanplumDeviceIdMode.ADVERTISING_ID);
        }
        if (MParticle.getInstance().getEnvironment().equals(MParticle.Environment.Development)) {
            Leanplum.enableVerboseLoggingInDevelopmentMode();
            Leanplum.setAppIdForDevelopmentMode(settings.get(APP_ID_KEY), settings.get(CLIENT_KEY_KEY));
        } else {
            Leanplum.setAppIdForProductionMode(settings.get(APP_ID_KEY), settings.get(CLIENT_KEY_KEY));
        }
        MParticle.getInstance().Identity().addIdentityStateListener(this);
        Map<MParticle.IdentityType, String> userIdentities = getUserIdentities();
        Map<String, Object> userAttributes = getAllUserAttributes();
        String userId = generateLeanplumUserId(MParticle.getInstance().Identity().getCurrentUser(), settings, userIdentities);

        if (!userAttributes.containsKey(LEANPLUM_EMAIL_USER_ATTRIBUTE)) {
            if (userIdentities.containsKey(MParticle.IdentityType.Email)) {
                userAttributes.put(LEANPLUM_EMAIL_USER_ATTRIBUTE, userIdentities.get(MParticle.IdentityType.Email));
            } else {
                userAttributes.put(LEANPLUM_EMAIL_USER_ATTRIBUTE, null);
            }
        }

        if (!TextUtils.isEmpty(userId)) {
            if (userAttributes.size() > 0) {
                Leanplum.start(context, userId, userAttributes);
            } else {
                Leanplum.start(context, userId);
            }
        } else if (userAttributes.size() > 0) {
            Leanplum.start(context, userAttributes);
        } else {
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
    public void onUserIdentified(MParticleUser mParticleUser) {
        Map<MParticle.IdentityType, String> userIdentities = mParticleUser.getUserIdentities();
        String userId = generateLeanplumUserId(mParticleUser, getSettings(), userIdentities);
        //first set userId to effectively switch users
        if (!KitUtils.isEmpty(userId)) {
            Leanplum.setUserId(userId);
        }
        //then set the attributes of the new user
        Map<String, Object> userAttributes = null;
        try {
            userAttributes = (Map<String, Object>) KitConfiguration.filterAttributes(this.getConfiguration().getUserAttributeFilters(), mParticleUser.getUserAttributes());
        }catch (Exception e) {
            userAttributes = new HashMap<String, Object>();
        }
        if (!userAttributes.containsKey(LEANPLUM_EMAIL_USER_ATTRIBUTE) && getConfiguration().shouldSetIdentity(MParticle.IdentityType.Email)) {
            if (userIdentities.containsKey(MParticle.IdentityType.Email)) {
                userAttributes.put(LEANPLUM_EMAIL_USER_ATTRIBUTE, userIdentities.get(MParticle.IdentityType.Email));
            } else {
                userAttributes.put(LEANPLUM_EMAIL_USER_ATTRIBUTE, null);
            }
        }
        Leanplum.setUserAttributes(userAttributes);
        //per Leanplum - it's a good idea to make sure the SDK refreshes itself
        Leanplum.forceContentUpdate();
    }

     String generateLeanplumUserId(MParticleUser user, Map<String, String> settings, Map<MParticle.IdentityType, String> userIdentities) {
        String userId = null;
        if (USER_ID_CUSTOMER_ID_VALUE.equalsIgnoreCase(settings.get(USER_ID_FIELD_KEY)) && getConfiguration().shouldSetIdentity(MParticle.IdentityType.CustomerId)) {
            userId = userIdentities.get(MParticle.IdentityType.CustomerId);
        } else if (USER_ID_EMAIL_VALUE.equalsIgnoreCase(settings.get(USER_ID_FIELD_KEY)) && getConfiguration().shouldSetIdentity(MParticle.IdentityType.Email)) {
            userId = userIdentities.get(MParticle.IdentityType.Email);
        } else if (user != null && USER_ID_MPID_VALUE.equalsIgnoreCase(settings.get(USER_ID_FIELD_KEY))) {
            userId = Long.toString(user.getId());
        }
        return userId;
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
        if (disableFirebase && legacyPushListener != null) {
            Intent service = new Intent(context, legacyPushListener);
            service.setAction("com.google.android.c2dm.intent.RECEIVE");
            service.putExtras(intent);
            context.startService(service);
        }
    }

    @Override
    public boolean onPushRegistration(String instanceId, String senderId) {
        if (disableFirebase) {
            LeanplumPushService.setGcmSenderId(senderId);
            LeanplumPushService.setGcmRegistrationId(instanceId);
        }
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
        //handled by IdentityStateListener
    }

    @Override
    public void removeUserIdentity(MParticle.IdentityType identityType) {
        //handled by IdentityStateListener
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
        Leanplum.track(Leanplum.PURCHASE_EVENT_NAME, product.getTotalAmount(), product.getName(), eventAttributes);
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

    private boolean isFirebaseDisabled() {
        try {
            legacyPushListener = Class.forName(LEGACY_PUSH_LISTENER_PATH);
            return true;
        } catch (ClassNotFoundException ignore) {
        }
        return false;
    }
}
