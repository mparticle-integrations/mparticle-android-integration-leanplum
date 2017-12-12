package com.mparticle.kits;


import android.content.Context;

import com.mparticle.identity.MParticleUser;

import junit.framework.Assert;

import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
        LeanplumKit kit = new LeanplumKit();
        Map<String, String> settings = new HashMap<>();
        settings.put(LeanplumKit.USER_ID_FIELD_KEY, LeanplumKit.USER_ID_MPID_VALUE);
        MParticleUser user = Mockito.mock(MParticleUser.class);
        Mockito.when(user.getId()).thenReturn(5L);
        String id = kit.generateLeanplumUserId(user, settings);
        Assert.assertEquals("5", id);

        Mockito.when(user.getId()).thenReturn(5L);
        id = kit.generateLeanplumUserId(null, settings);
        Assert.assertNull(id);

    }
}
