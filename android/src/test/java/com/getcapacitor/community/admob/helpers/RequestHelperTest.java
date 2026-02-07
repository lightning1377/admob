package com.getcapacitor.community.admob.helpers;

import static org.mockito.Mockito.verify;

import android.os.Bundle;
import com.getcapacitor.community.admob.models.AdOptions;
import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.android.gms.ads.AdRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequestHelperTest {

    @Test
    @DisplayName("#createRequest should add npa to the bundle if npa is requested")
    void npa() {
        try (MockedConstruction<AdRequest.Builder> adRequestBuilderMockedConstruction = Mockito.mockConstruction(AdRequest.Builder.class)) {
            try (MockedConstruction<Bundle> bundleMockedConstruction = Mockito.mockConstruction(Bundle.class)) {
                AdOptions adOptions = new AdOptions.TesterAdOptionsBuilder().setNpa(true).build();

                // Act
                RequestHelper.createRequest(adOptions);

                Bundle mockedBundle = bundleMockedConstruction.constructed().get(0);
                AdRequest.Builder adRequestBuilder = adRequestBuilderMockedConstruction.constructed().get(0);
                verify(mockedBundle).putString("npa", "1");
                verify(adRequestBuilder).addNetworkExtrasBundle(AdMobAdapter.class, mockedBundle);
            }
        }
    }

    @Test
    @DisplayName("#createRequest should add collapsible bottom to the bundle if isCollapsible is requested with BOTTOM_CENTER position")
    void isCollapsibleBottom() {
        try (MockedConstruction<AdRequest.Builder> adRequestBuilderMockedConstruction = Mockito.mockConstruction(AdRequest.Builder.class)) {
            try (MockedConstruction<Bundle> bundleMockedConstruction = Mockito.mockConstruction(Bundle.class)) {
                AdOptions adOptions = new AdOptions.TesterAdOptionsBuilder().setIsCollapsible(true).setPosition("BOTTOM_CENTER").build();

                // Act
                RequestHelper.createRequest(adOptions);

                Bundle mockedBundle = bundleMockedConstruction.constructed().get(0);
                AdRequest.Builder adRequestBuilder = adRequestBuilderMockedConstruction.constructed().get(0);
                verify(mockedBundle).putString("collapsible", "bottom");
                verify(adRequestBuilder).addNetworkExtrasBundle(AdMobAdapter.class, mockedBundle);
            }
        }
    }

    @Test
    @DisplayName("#createRequest should add collapsible top to the bundle if isCollapsible is requested with TOP_CENTER position")
    void isCollapsibleTop() {
        try (MockedConstruction<AdRequest.Builder> adRequestBuilderMockedConstruction = Mockito.mockConstruction(AdRequest.Builder.class)) {
            try (MockedConstruction<Bundle> bundleMockedConstruction = Mockito.mockConstruction(Bundle.class)) {
                AdOptions adOptions = new AdOptions.TesterAdOptionsBuilder().setIsCollapsible(true).setPosition("TOP_CENTER").build();

                // Act
                RequestHelper.createRequest(adOptions);

                Bundle mockedBundle = bundleMockedConstruction.constructed().get(0);
                AdRequest.Builder adRequestBuilder = adRequestBuilderMockedConstruction.constructed().get(0);
                verify(mockedBundle).putString("collapsible", "top");
                verify(adRequestBuilder).addNetworkExtrasBundle(AdMobAdapter.class, mockedBundle);
            }
        }
    }
}
