/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.testsuite.i18n;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

import org.apache.http.impl.client.CloseableHttpClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.engines.ApacheHttpClient43Engine;
import org.junit.Assert;
import org.junit.Test;
import org.keycloak.OAuth2Constants;
import org.keycloak.adapters.HttpClientBuilder;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.locale.LocaleSelectorProvider;
import org.keycloak.models.UserModel;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.testsuite.admin.ApiUtil;
import org.keycloak.testsuite.pages.AppPage;
import org.keycloak.testsuite.pages.LanguageComboboxAwarePage;
import org.keycloak.testsuite.pages.LoginPage;

import jakarta.ws.rs.core.Response;
import org.jboss.arquillian.graphene.page.Page;
import org.keycloak.testsuite.pages.LoginPasswordUpdatePage;
import org.keycloak.testsuite.pages.OAuthGrantPage;
import org.keycloak.testsuite.util.IdentityProviderBuilder;
import org.openqa.selenium.Cookie;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

/**
 * @author <a href="mailto:gerbermichi@me.com">Michael Gerber</a>
 * @author Stan Silvert ssilvert@redhat.com (C) 2016 Red Hat Inc.
 */
public class LoginPageTest extends AbstractI18NTest {

    @Page
    protected AppPage appPage;

    @Page
    protected LoginPage loginPage;

    @Page
    protected LoginPasswordUpdatePage changePasswordPage;

    @Page
    protected OAuthGrantPage grantPage;


    @Override
    public void configureTestRealm(RealmRepresentation testRealm) {
        testRealm.addIdentityProvider(IdentityProviderBuilder.create()
                .providerId("github")
                .alias("github")
                .build());
        testRealm.addIdentityProvider(IdentityProviderBuilder.create()
                .providerId("saml")
                .alias("mysaml")
                .build());
        testRealm.addIdentityProvider(IdentityProviderBuilder.create()
                .providerId("oidc")
                .alias("myoidc")
                .displayName("MyOIDC")
                .build());

    }

    @Test
    public void languageDropdown() {
        loginPage.open();
        assertEquals("English", loginPage.getLanguageDropdownText());

        switchLanguageToGermanAndBack("Username or email", "Benutzername oder E-Mail", loginPage);
    }

    @Test
    public void uiLocalesParameter() {
        loginPage.open();
        assertEquals("English", loginPage.getLanguageDropdownText());

        //test if cookie works
        oauth.uiLocales("de");
        loginPage.open();
        assertEquals("Deutsch", loginPage.getLanguageDropdownText());

        driver.manage().deleteAllCookies();
        loginPage.open();
        assertEquals("Deutsch", loginPage.getLanguageDropdownText());

        oauth.uiLocales("en de");
        driver.manage().deleteAllCookies();
        loginPage.open();
        assertEquals("English", loginPage.getLanguageDropdownText());

        oauth.uiLocales("fr de");
        driver.manage().deleteAllCookies();
        loginPage.open();
        assertEquals("Deutsch", loginPage.getLanguageDropdownText());
    }

    @Test
    public void acceptLanguageHeader() throws IOException {
        try(CloseableHttpClient httpClient = (CloseableHttpClient) new HttpClientBuilder().build()) {
            ApacheHttpClient43Engine engine = new ApacheHttpClient43Engine(httpClient);
            ResteasyClient client = ((ResteasyClientBuilder) ResteasyClientBuilder.newBuilder()).httpEngine(engine).build();

            loginPage.open();

            try(Response responseDe = client.target(driver.getCurrentUrl()).request().acceptLanguage("de").get()) {
                Assert.assertTrue(responseDe.readEntity(String.class).contains("Anmeldung bei test"));

                try(Response responseEn = client.target(driver.getCurrentUrl()).request().acceptLanguage("en").get()) {
                    Assert.assertTrue(responseEn.readEntity(String.class).contains("Sign in to test"));
                }
            }

            client.close();
        }
    }

    @Test
    public void testIdentityProviderCapitalization(){
        loginPage.open();
        assertEquals("GitHub", loginPage.findSocialButton("github").getText());
        assertEquals("mysaml", loginPage.findSocialButton("mysaml").getText());
        assertEquals("MyOIDC", loginPage.findSocialButton("myoidc").getText());
    }


    // KEYCLOAK-3887
    @Test
    public void languageChangeRequiredActions() {
        UserResource user = ApiUtil.findUserByUsernameId(testRealm(), "test-user@localhost");
        UserRepresentation userRep = user.toRepresentation();
        userRep.setRequiredActions(Arrays.asList(UserModel.RequiredAction.UPDATE_PASSWORD.toString()));
        user.update(userRep);

        loginPage.open();

        loginPage.login("test-user@localhost", "password");
        changePasswordPage.assertCurrent();
        assertEquals("English", changePasswordPage.getLanguageDropdownText());

        // Switch language
        switchLanguageToGermanAndBack("Update password", "Passwort aktualisieren", changePasswordPage);

        // Update password
        changePasswordPage.changePassword("password", "password");

        assertEquals(AppPage.RequestType.AUTH_RESPONSE, appPage.getRequestType());
        Assert.assertNotNull(oauth.getCurrentQuery().get(OAuth2Constants.CODE));
    }


    // KEYCLOAK-3887
    @Test
    public void languageChangeConsentScreen() {
        // Set client, which requires consent
        oauth.clientId("third-party");

        loginPage.open();

        loginPage.login("test-user@localhost", "password");

        grantPage.assertCurrent();
        assertEquals("English", grantPage.getLanguageDropdownText());

        // Switch language
        switchLanguageToGermanAndBack("Do you grant these access privileges?", "Wollen Sie diese Zugriffsrechte", changePasswordPage);

        // Confirm grant
        grantPage.accept();

        assertEquals(AppPage.RequestType.AUTH_RESPONSE, appPage.getRequestType());
        Assert.assertNotNull(oauth.getCurrentQuery().get(OAuth2Constants.CODE));

        // Revert client
        oauth.clientId("test-app");
    }

    @Test
    public void languageUserUpdates() {
        loginPage.open();
        loginPage.openLanguage("Deutsch");

        assertEquals("Deutsch", loginPage.getLanguageDropdownText());

        Cookie localeCookie = driver.manage().getCookieNamed(LocaleSelectorProvider.LOCALE_COOKIE);
        assertEquals("de", localeCookie.getValue());

        loginPage.login("test-user@localhost", "password");

        UserResource user = ApiUtil.findUserByUsernameId(testRealm(), "test-user@localhost");
        UserRepresentation userRep = user.toRepresentation();
        assertEquals("de", userRep.getAttributes().get("locale").get(0));

        String code = oauth.getCurrentQuery().get(OAuth2Constants.CODE);
        String idTokenHint = oauth.doAccessTokenRequest(code, "password").getIdToken();
        appPage.logout(idTokenHint);

        loginPage.open();

        assertEquals("Deutsch", loginPage.getLanguageDropdownText());

        userRep.getAttributes().remove("locale");
        user.update(userRep);

        loginPage.open();
        loginPage.login("test-user@localhost", "password");

        // User locale should not be updated due to previous cookie
        userRep = user.toRepresentation();
        Assert.assertNull(userRep.getAttributes());

        code = oauth.getCurrentQuery().get(OAuth2Constants.CODE);
        idTokenHint = oauth.doAccessTokenRequest(code, "password").getIdToken();
        appPage.logout(idTokenHint);

        loginPage.open();

        // Cookie should be removed as last user to login didn't have a locale
        localeCookie = driver.manage().getCookieNamed(LocaleSelectorProvider.LOCALE_COOKIE);
        Assert.assertNull(localeCookie);
    }

    @Test
    public void realmLocalizationMessagesAreApplied() {
        String realmLocalizationMessageKey = "loginAccountTitle";

        String realmLocalizationMessageValueEn = "Localization Test EN";
        saveLocalizationText(Locale.ENGLISH.toLanguageTag(), realmLocalizationMessageKey,
                realmLocalizationMessageValueEn);
        String realmLocalizationMessageValueDe = "Localization Test DE";
        saveLocalizationText(Locale.GERMAN.toLanguageTag(), realmLocalizationMessageKey,
                realmLocalizationMessageValueDe);

        loginPage.open();
        switchLanguageToGermanAndBack(realmLocalizationMessageValueEn, realmLocalizationMessageValueDe, loginPage);
    }

    // KEYCLOAK-18590
    @Test
    public void realmLocalizationMessagesAreNotCachedWithinTheTheme() {
        final String locale = Locale.ENGLISH.toLanguageTag();

        final String realmLocalizationMessageKey = "loginAccountTitle";
        final String realmLocalizationMessageValue = "Localization Test";
        
        saveLocalizationText(locale, realmLocalizationMessageKey, realmLocalizationMessageValue);
        loginPage.open();
        assertThat(driver.getPageSource(), containsString(realmLocalizationMessageValue));

        testRealm().localization().deleteRealmLocalizationText(locale, realmLocalizationMessageKey);
        loginPage.open();
        assertThat(driver.getPageSource(), not(containsString(realmLocalizationMessageValue)));
    }

    private void saveLocalizationText(String locale, String key, String value) {
        testRealm().localization().saveRealmLocalizationText(locale, key, value);
        getCleanup().addLocalization(locale);
    }

    private void switchLanguageToGermanAndBack(String expectedEnglishMessage, String expectedGermanMessage, LanguageComboboxAwarePage page) {
        // Switch language to Deutsch
        page.openLanguage("Deutsch");
        assertEquals("Deutsch", page.getLanguageDropdownText());
        String pageSource = driver.getPageSource();
        assertThat(pageSource, not(containsString(expectedEnglishMessage)));
        assertThat(pageSource, containsString(expectedGermanMessage));

        // Revert language
        page.openLanguage("English");
        assertEquals("English", page.getLanguageDropdownText());
        pageSource = driver.getPageSource();
        assertThat(pageSource, containsString(expectedEnglishMessage));
        assertThat(pageSource, not(containsString(expectedGermanMessage)));
    }
}
