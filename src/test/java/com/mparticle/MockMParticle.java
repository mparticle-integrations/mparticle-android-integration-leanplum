package com.mparticle;

import com.mparticle.MParticle;
import com.mparticle.identity.IdentityApi;

import org.mockito.Mockito;

public class MockMParticle extends MParticle {

        public MockMParticle() {
            mIdentityApi = Mockito.mock(IdentityApi.class);
        }

        public void setAndroidIdDisabled(boolean disabled) {
            sAndroidIdEnabled = !disabled;
        }

}
