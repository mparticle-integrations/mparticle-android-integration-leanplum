package com.mparticle.kits;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.leanplum.Leanplum;
import com.leanplum.LeanplumActivityHelper;
import com.leanplum.LeanplumDeviceIdMode;
import com.mparticle.MPEvent;
import com.mparticle.MParticle;
import com.mparticle.commerce.CommerceEvent;
import com.mparticle.commerce.Product;
import com.mparticle.consent.ConsentState;
import com.mparticle.identity.MParticleUser;
import com.mparticle.internal.Logger;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


public class LeanplumKit extends KitIntegration implements KitIntegration.UserAttributeListener, KitIntegration.EventListener, KitIntegration.CommerceListener, KitIntegration.IdentityListener, KitIntegration.PushListener {
    private final static String APP_ID_KEY = "appId";
    private final static String CLIENT_KEY_KEY = "clientKey";
    final static String USER_ID_FIELD_KEY = "userIdField";
    final static String USER_ID_CUSTOMER_ID_VALUE = "customerId";
    final static String USER_ID_EMAIL_VALUE = "email";
    final static String USER_ID_MPID_VALUE = "mpid";
    final static String LEANPLUM_EMAIL_USER_ATTRIBUTE = "email";
    final static String DEVICE_ID_TYPE = "androidDeviceId";
    final static String DEVICE_ID_TYPE_GOOGLE_AD_ID = "gaid";
    final static String DEVICE_ID_TYPE_ANDROID_ID = "androidId";
    final static String DEVICE_ID_TYPE_DAS = "das";

    @Override
    protected List<ReportingMessage> onKitCreate(Map<String, String> settings, Context context) {
        String deviceIdType = settings.get(DEVICE_ID_TYPE);
        setDeviceIdType(deviceIdType);
        if (MParticle.getInstance().getEnvironment().equals(MParticle.Environment.Development)) {
            Leanplum.enableVerboseLoggingInDevelopmentMode();
            Leanplum.setAppIdForDevelopmentMode(settings.get(APP_ID_KEY), settings.get(CLIENT_KEY_KEY));
        } else {
            Leanplum.setAppIdForProductionMode(settings.get(APP_ID_KEY), settings.get(CLIENT_KEY_KEY));
        }
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
    public void onIdentifyCompleted(MParticleUser mParticleUser, FilteredIdentityApiRequest filteredIdentityApiRequest) {
        //do nothing
    }

    @Override
    public void onLoginCompleted(MParticleUser mParticleUser, FilteredIdentityApiRequest filteredIdentityApiRequest) {
        //do nothing
    }

    @Override
    public void onLogoutCompleted(MParticleUser mParticleUser, FilteredIdentityApiRequest filteredIdentityApiRequest) {
        //do nothing
    }

    @Override
    public void onModifyCompleted(MParticleUser mParticleUser, FilteredIdentityApiRequest filteredIdentityApiRequest) {
        //do nothing
    }

    @Override
    public void onUserIdentified(MParticleUser mParticleUser) {
        final Map<MParticle.IdentityType, String> userIdentities = mParticleUser.getUserIdentities();
        String userId = generateLeanplumUserId(mParticleUser, getSettings(), userIdentities);
        //first set userId to effectively switch users
        if (!KitUtils.isEmpty(userId)) {
            Leanplum.setUserId(userId);
        }
        //then set the attributes of the new user
        try {
            mParticleUser.getUserAttributes(new com.mparticle.UserAttributeListener() {
                @Override
                public void onUserAttributesReceived(@Nullable Map<String, String> userAttributeSingles, @Nullable Map<String, List<String>> userAttributeLists, @Nullable Long mpid) {
                    Map<String, Object> userAttributes = new HashMap(userAttributeSingles);
                    userAttributes.putAll(userAttributeLists);
                    setLeanplumUserAttributes(userIdentities, userAttributes);
                }
            });
        } catch (Exception e) {
            Logger.warning(e, "Unable to fetch User Attributes");
        }
    }

    private void setLeanplumUserAttributes(Map<MParticle.IdentityType, String> userIdentities, Map<String, Object> userAttributes) {
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
    public void onSetUserAttribute(String key, Object value, FilteredMParticleUser user) {
        Map<String, Object> attributes = new HashMap<String, Object>(1);
        attributes.put(key, value);
        Leanplum.setUserAttributes(attributes);
    }

    @Override
    public void onSetUserTag(String s, FilteredMParticleUser filteredMParticleUser) {

    }

    @Override
    public void onSetUserAttributeList(String key, List<String> list, FilteredMParticleUser user) {
        Map<String, Object> attributes = new HashMap<String, Object>(1);
        attributes.put(key, list);
        Leanplum.setUserAttributes(attributes);
    }

    @Override
    public void onIncrementUserAttribute(String key, int incrementedBy, String newValue, FilteredMParticleUser filteredMParticleUser) {
        Map<String, Object> attributes = new HashMap<String, Object>(1);
        attributes.put(key, newValue);
        Leanplum.setUserAttributes(attributes);
    }

    @Override
    public boolean supportsAttributeLists() {
        return true;
    }

    @Override
    public void onConsentStateUpdated(ConsentState consentState, ConsentState consentState1, FilteredMParticleUser filteredMParticleUser) {

    }

    @Override
    public void onSetAllUserAttributes(Map<String, String> attributes, Map<String, List<String>> attributeLists, FilteredMParticleUser user) {
        //we set user attributes on start so there's no point in doing it here as well.
    }

    @Override
    public void onRemoveUserAttribute(String key, FilteredMParticleUser user) {
        Map<String, Object> attribute = new HashMap<String, Object>(1);
        attribute.put(key, null);
        Leanplum.setUserAttributes(attribute);
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
        Leanplum.track(mpEvent.getEventName(), mpEvent.getCustomAttributes());
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
        messageList.add(ReportingMessage.fromEvent(this, new MPEvent.Builder(eventName, MParticle.EventType.Transaction).customAttributes(attributes).build()));
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

    void setDeviceIdType(String deviceIdType) {
        if (DEVICE_ID_TYPE_ANDROID_ID.equals(deviceIdType) && MParticle.isAndroidIdEnabled()) {
            Leanplum.setDeviceIdMode(LeanplumDeviceIdMode.ANDROID_ID);
        } else if (DEVICE_ID_TYPE_GOOGLE_AD_ID.equals(deviceIdType)) {
            Leanplum.setDeviceIdMode(LeanplumDeviceIdMode.ADVERTISING_ID);
        } else if (DEVICE_ID_TYPE_DAS.equals(deviceIdType)) {
            Leanplum.setDeviceId(MParticle.getInstance().Identity().getDeviceApplicationStamp());
        }
    }

    @Override
    public boolean willHandlePushMessage(Intent intent) {
        return intent.getExtras().containsKey("lp_version");
    }

    @Override
    public void onPushMessageReceived(Context context, Intent intent) {
        //Firebase only
    }

    @Override
    public boolean onPushRegistration(String s, String s1) {
        //Firebase only
        return false;
    }
}
