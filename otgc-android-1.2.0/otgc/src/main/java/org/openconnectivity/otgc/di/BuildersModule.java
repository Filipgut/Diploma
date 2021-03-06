/* ******************************************************************
 *
 * Copyright 2018 DEKRA Testing and Certification, S.A.U. All Rights Reserved.
 *
 * ******************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ******************************************************************/
package org.openconnectivity.otgc.di;

import org.openconnectivity.otgc.accesscontrol.presentation.view.AccessControlActivity;
import org.openconnectivity.otgc.accesscontrol.presentation.view.AceActivity;
import org.openconnectivity.otgc.client.ClientBuildersModule;
import org.openconnectivity.otgc.client.presentation.view.GenericClientActivity;
import org.openconnectivity.otgc.credential.presentation.view.CredActivity;
import org.openconnectivity.otgc.credential.presentation.view.CredentialsActivity;
import org.openconnectivity.otgc.devicelist.DeviceListBuildersModule;
import org.openconnectivity.otgc.devicelist.presentation.view.DeviceListActivity;
import org.openconnectivity.otgc.login.presentation.view.LoginActivity;
import org.openconnectivity.otgc.splash.presentation.view.SplashActivity;
import org.openconnectivity.otgc.wlanscan.presentation.view.WlanScanActivity;

import dagger.Module;
import dagger.android.ContributesAndroidInjector;

@Module
public interface BuildersModule {

    @ContributesAndroidInjector
    abstract SplashActivity bindSplashActivity();

    @ContributesAndroidInjector
    abstract LoginActivity bindLoginActivity();

    @ContributesAndroidInjector
    abstract WlanScanActivity bindWlanScanActivity();

    @ContributesAndroidInjector(modules = DeviceListBuildersModule.class)
    abstract DeviceListActivity bindDevicesActivity();

    @ContributesAndroidInjector
    abstract AccessControlActivity bindAccessControlActivity();

    @ContributesAndroidInjector
    abstract AceActivity bindAceActivity();

    @ContributesAndroidInjector
    abstract CredentialsActivity bindCredentialsActivity();

    @ContributesAndroidInjector
    abstract CredActivity bindCredActivity();

    @ContributesAndroidInjector(modules = ClientBuildersModule.class)
    abstract GenericClientActivity bindGenericClientActivity();
}
