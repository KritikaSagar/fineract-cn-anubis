/*
 * Copyright 2017 The Mifos Initiative.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.mifos.anubis;

import io.mifos.anubis.api.v1.client.Anubis;
import io.mifos.anubis.api.v1.client.AnubisApiFactory;
import io.mifos.anubis.api.v1.domain.AllowedOperation;
import io.mifos.anubis.api.v1.domain.Signature;
import io.mifos.anubis.test.v1.TenantApplicationSecurityEnvironmentTestRule;
import io.mifos.core.api.context.AutoSeshat;
import io.mifos.core.api.context.AutoUserContext;
import io.mifos.core.api.util.InvalidTokenException;
import io.mifos.core.api.util.NotFoundException;
import io.mifos.core.lang.AutoTenantContext;
import io.mifos.core.test.fixture.TenantDataStoreTestContext;
import org.junit.Assert;
import org.junit.Test;

import java.security.interfaces.RSAPublicKey;

/**
 * @author Myrle Krantz
 */
public class TestAnubisInitialize extends AbstractSimpleTest {

  @Test
  public void testBrokenToken()
  {
    try (final TenantDataStoreTestContext ignored = TenantDataStoreTestContext.forRandomTenantName(cassandraInitializer)) {
      example.uninitialize(); //make sure the internal initialize variable isn't set before we start.

      final String brokenSeshatToken = "hmmmm, this doesn't look like a token?";

      try {

        final Anubis anubis = AnubisApiFactory.create(testEnvironment.serverURI(), logger);

        try (final AutoSeshat ignored2 = new AutoSeshat(brokenSeshatToken)) {
          final TenantApplicationSecurityEnvironmentTestRule securityMock = new TenantApplicationSecurityEnvironmentTestRule(testEnvironment);

          final String keyTimestamp = securityMock.getSystemSecurityEnvironment().tenantKeyTimestamp();
          final RSAPublicKey publicKey = securityMock.getSystemSecurityEnvironment().tenantPublicKey();
          final Signature signature = new Signature(publicKey.getModulus(), publicKey.getPublicExponent());

          anubis.createSignatureSet(keyTimestamp, signature);
        }

        Assert.fail("A call with a broken token should result in an exception thrown.");
      } catch (final InvalidTokenException e) {
        Assert.assertFalse("Service init code should not have been reached with a broken token.",
                example.initialized());
      }
    }
  }

  @Test
  public void testHappyCase() {
    try (final TenantDataStoreTestContext ignored = TenantDataStoreTestContext.forRandomTenantName(cassandraInitializer)) {
      initialize();
    }
  }

  @Test
  public void testReinitialize() {
    try (final TenantDataStoreTestContext ignored = TenantDataStoreTestContext.forRandomTenantName(cassandraInitializer)) {

      initialize();

      initialize();
    }
  }

  @Test
  public void testTwoTenants() {

    try (final TenantDataStoreTestContext ignored = TenantDataStoreTestContext.forRandomTenantName(cassandraInitializer)) {
      initialize();
    }

    try (final TenantDataStoreTestContext ignored = TenantDataStoreTestContext.forRandomTenantName(cassandraInitializer)) {
      initialize();
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNoTenant() {
    try (final AutoTenantContext ignored = new AutoTenantContext("")) {
      initialize();
    }
  }

  @Test(expected = NotFoundException.class)
  public void testNonExistentTenant() {
    try (final AutoTenantContext ignored = new AutoTenantContext("monster_under_your_bed")) {
      initialize();
    }
  }

  @Test(expected = InvalidTokenException.class)
  public void testAuthenticateWithoutInitialize() {
    try (final TenantDataStoreTestContext ignored = TenantDataStoreTestContext.forRandomTenantName(cassandraInitializer)) {

      final TenantApplicationSecurityEnvironmentTestRule tenantApplicationSecurityEnvironment
              = new TenantApplicationSecurityEnvironmentTestRule(testEnvironment);
      final String permissionToken = tenantApplicationSecurityEnvironment.getPermissionToken("bubba", "foo", AllowedOperation.READ);
      try (final AutoUserContext ignored2 = new AutoUserContext("bubba", permissionToken)) {
        Assert.assertFalse(example.foo());
        Assert.fail("Not found exception should be thrown when authentication is attempted ");
      }
    }
  }

  private void initialize() {
    final TenantApplicationSecurityEnvironmentTestRule tenantApplicationSecurityEnvironment
            = new TenantApplicationSecurityEnvironmentTestRule(testEnvironment);
    tenantApplicationSecurityEnvironment.initializeTenantInApplication();

    try (final AutoUserContext ignored = tenantApplicationSecurityEnvironment.createAutoUserContext("x")) {
      Assert.assertTrue(example.initialized());
    }
  }

  //TODO: tests still needed for getting application key and deleting keysets.
}
