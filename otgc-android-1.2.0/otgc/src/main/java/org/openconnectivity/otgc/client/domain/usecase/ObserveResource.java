/*
 * *****************************************************************
 *
 *  Copyright 2018 DEKRA Testing and Certification, S.A.U. All Rights Reserved.
 *
 *  ******************************************************************
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  ******************************************************************
 */

package org.openconnectivity.otgc.client.domain.usecase;

import org.iotivity.base.OcRepresentation;
import org.openconnectivity.otgc.client.data.repository.ResourceRepository;
import org.openconnectivity.otgc.common.data.repository.IotivityRepository;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.Observable;

public class ObserveResource {

    private final IotivityRepository iotivityRepository;
    private final ResourceRepository resourceRepository;

    @Inject
    public ObserveResource(IotivityRepository iotivityRepository,
                                  ResourceRepository resourceRepository) {
        this.iotivityRepository = iotivityRepository;
        this.resourceRepository = resourceRepository;
    }

    public Observable<OcRepresentation> execute(String deviceId, String resourceUri, List<String> resourceTypes, List<String> interfaces) {
        return iotivityRepository.getDeviceCoapsIpv6Host(deviceId)
                .flatMap(host -> iotivityRepository.constructResource(host, resourceUri, resourceTypes, interfaces))
                .flatMapObservable(resourceRepository::observeResource);
    }
}

