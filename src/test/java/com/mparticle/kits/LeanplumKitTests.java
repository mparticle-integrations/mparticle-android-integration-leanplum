package com.mparticle.kits;


import android.content.Context;

import com.leanplum.Leanplum;
import com.leanplum.LeanplumDeviceIdMode;
import com.mparticle.MParticle;
import com.mparticle.identity.IdentityApi;
import com.mparticle.identity.MParticleUser;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;

@RunWith(PowerMockRunner.class)
public class LeanplumKitTests {

    private KitIntegration getKit() {
        return new LeanplumKit();
    }

    @Test
    public void testGetName() throws Exception {
        String name = getKit().getName();
        assertTrue(name != null && name.length() > 0);
    }

    /**
     * Kit *should* throw an exception when they're initialized with the wrong settings.
     *
     */
    @Test
    public void testOnKitCreate() throws Exception{
        Exception e = null;
        try {
            KitIntegration kit = getKit();
            Map settings = new HashMap<>();
            settings.put("fake setting", "fake");
            kit.onKitCreate(settings, Mockito.mock(Context.class));
        }catch (Exception ex) {
            e = ex;
        }
        assertNotNull(e);
    }

    @Test
    public void testClassName() throws Exception {
        KitIntegrationFactory factory = new KitIntegrationFactory();
        Map<Integer, String> integrations = factory.getKnownIntegrations();
        String className = getKit().getClass().getName();
        for (Map.Entry<Integer, String> entry : integrations.entrySet()) {
            if (entry.getValue().equals(className)) {
                return;
            }
        }
        fail(className + " not found as a known integration.");
    }

    @Test
    public void testGenerateMpidUserId() throws Exception {
        Map<String, String> settings = new HashMap<>();
        settings.put(LeanplumKit.USER_ID_FIELD_KEY, LeanplumKit.USER_ID_MPID_VALUE);

        Map<MParticle.IdentityType, String> userIdentities = new HashMap<>();
        userIdentities.put(MParticle.IdentityType.Email, "foo email");
        userIdentities.put(MParticle.IdentityType.CustomerId, "foo customer id");

        MParticleUser user = Mockito.mock(MParticleUser.class);
        Mockito.when(user.getId()).thenReturn(5L);

        LeanplumKit kit = new LeanplumKit();
        kit.setConfiguration(Mockito.mock(KitConfiguration.class));
        String id = kit.generateLeanplumUserId(user, settings, userIdentities);
        Assert.assertEquals("5", id);

        id = kit.generateLeanplumUserId(null, settings, userIdentities);
        Assert.assertNull(id);
    }

    @Test
    public void testGenerateEmailUserId() throws Exception {
        Map<String, String> settings = new HashMap<>();
        settings.put(LeanplumKit.USER_ID_FIELD_KEY, LeanplumKit.USER_ID_EMAIL_VALUE);

        Map<MParticle.IdentityType, String> userIdentities = new HashMap<>();
        userIdentities.put(MParticle.IdentityType.Email, "foo email");
        userIdentities.put(MParticle.IdentityType.CustomerId, "foo customer id");

        MParticleUser user = Mockito.mock(MParticleUser.class);
        Mockito.when(user.getId()).thenReturn(5L);
        LeanplumKit kit = new LeanplumKit();
        KitConfiguration mockConfiguraiton = Mockito.mock(KitConfiguration.class);
        Mockito.when(mockConfiguraiton.shouldSetIdentity(any(MParticle.IdentityType.class))).thenReturn(true);
        kit.setConfiguration(mockConfiguraiton);
        String id = kit.generateLeanplumUserId(user, settings, userIdentities);
        Assert.assertEquals("foo email", id);
        userIdentities.remove(MParticle.IdentityType.Email);
        id = kit.generateLeanplumUserId(user, settings, userIdentities);
        Assert.assertNull(id);
    }

    @Test
    public void testGenerateCustomerIdlUserId() throws Exception {
        Map<String, String> settings = new HashMap<>();
        settings.put(LeanplumKit.USER_ID_FIELD_KEY, LeanplumKit.USER_ID_CUSTOMER_ID_VALUE);

        Map<MParticle.IdentityType, String> userIdentities = new HashMap<>();
        userIdentities.put(MParticle.IdentityType.Email, "foo email");
        userIdentities.put(MParticle.IdentityType.CustomerId, "foo customer id");

        MParticleUser user = Mockito.mock(MParticleUser.class);
        Mockito.when(user.getId()).thenReturn(5L);
        LeanplumKit kit = new LeanplumKit();
        KitConfiguration mockConfiguraiton = Mockito.mock(KitConfiguration.class);
        Mockito.when(mockConfiguraiton.shouldSetIdentity(any(MParticle.IdentityType.class))).thenReturn(true);
        kit.setConfiguration(mockConfiguraiton);
        String id = kit.generateLeanplumUserId(user, settings, userIdentities);
        Assert.assertEquals("foo customer id", id);
        userIdentities.remove(MParticle.IdentityType.CustomerId);
        id = kit.generateLeanplumUserId(user, settings, userIdentities);
        Assert.assertNull(id);
    }

    @Test
    @PrepareForTest({MParticle.class})
    public void testDeviceIdType() {
        PowerMockito.mockStatic(MParticle.class);

        String mockDas = "mockDasValue";
        PowerMockito.when(MParticle.getInstance()).thenReturn(Mockito.mock(MParticle.class));
        Mockito.when(MParticle.getInstance().Identity()).thenReturn(Mockito.mock(IdentityApi.class));
        Mockito.when(MParticle.getInstance().Identity().getDeviceApplicationStamp()).thenReturn(mockDas);
        PowerMockito.when(MParticle.isAndroidIdDisabled()).thenReturn(false);

        LeanplumKit leanplumKit = new LeanplumKit();

        leanplumKit.setDeviceIdType(LeanplumKit.DEVICE_ID_TYPE_ANDROID_ID);
        assertEquals(LeanplumDeviceIdMode.ANDROID_ID, Leanplum.getMode());
        assertNull(Leanplum.getDeviceId());

        Leanplum.clear();

        leanplumKit.setDeviceIdType(LeanplumKit.DEVICE_ID_TYPE_GOOGLE_AD_ID);
        assertEquals(LeanplumDeviceIdMode.ADVERTISING_ID, Leanplum.getMode());
        assertNull(Leanplum.getDeviceId());

        Leanplum.clear();

        leanplumKit.setDeviceIdType(LeanplumKit.DEVICE_ID_TYPE_DAS);
        assertEquals(mockDas, Leanplum.getDeviceId());
        assertNull(Leanplum.getMode());

        Leanplum.clear();

        leanplumKit.setDeviceIdType("adrbsdtb");
        assertNull(Leanplum.getDeviceId());
        assertNull(Leanplum.getMode());

        leanplumKit.setDeviceIdType(null);
        assertNull(Leanplum.getDeviceId());
        assertNull(Leanplum.getMode());

        Leanplum.clear();

        PowerMockito.when(MParticle.isAndroidIdDisabled()).thenReturn(true);
        leanplumKit.setDeviceIdType(LeanplumKit.DEVICE_ID_TYPE_ANDROID_ID);
        assertNull(Leanplum.getMode());
        assertNull(Leanplum.getDeviceId());


    }
}
