/**
 *  Copyright 2005-2015 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package io.fabric8.rest;

import io.fabric8.api.Container;
import io.fabric8.api.Containers;
import io.fabric8.api.FabricRequirements;
import io.fabric8.api.FabricService;
import io.fabric8.api.Profile;
import io.fabric8.api.ProfileRequirements;
import io.fabric8.api.ProfileService;
import io.fabric8.api.Profiles;
import io.fabric8.api.jmx.ProfileDTO;
import io.fabric8.common.util.Objects;
import io.fabric8.core.jmx.Links;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import java.io.IOException;
import java.util.List;

/**
 * Represents a Profile resource
 */
@Produces("application/json")
public class ProfileResource extends ResourceSupport {
    
    private final Profile profile;

    public ProfileResource(ResourceSupport parent, Profile profile) {
        super(parent, (profile.isOverlay() ? "/overlay" : "profile/" + profile.getId()));
        this.profile = profile;
    }

    @Override
    public String toString() {
        return "ProfileResource{" +
                "profile=" + profile +
                '}';
    }

    @GET
    public ProfileDTO details() {
        String overlay = profile.isOverlay() ? null : getLink("overlay");
        return new ProfileDTO(profile, getLink("containers"), overlay, getLink("requirements"), getLink("fileNames"));
    }

    @DELETE
    public void deleteProfile() {
        FabricService fabricService = getFabricService();
        Objects.notNull(fabricService, "fabricService");
        ProfileService profileService = getProfileService();
        Objects.notNull(profileService, "profileService");
        profileService.deleteProfile(fabricService, profile.getVersion(), profile.getId(), true);
    }


    /**
     * Returns the list of container ID links for this profile
     */
    @GET
    @Path("containers")
    public Map<String, String> containers() {
        FabricService fabricService = getFabricService();
        if (fabricService != null) {
            List<Container> containers = Containers.containersForProfile(fabricService.getContainers(), profile.getId(), profile.getVersion());
            List<String> keys = Containers.containerIds(containers);

            // lets get the link to the fabric
            ResourceSupport node = this;
            for (int i = 0; i < 2 && node != null; i++) {
                node = node.getParent();
            }
            String baseURI = node != null ?  node.getBaseUri() : "";
            return Links.mapIdsToLinks(keys, baseURI + "/container/");
        } else {
            noFabricService();
        }
        return Collections.emptyMap();
    }

    /**
     * Returns the overlay (effective) profile
     */
    @Path("overlay")
    public ProfileResource overlay() {
        if (!profile.isOverlay()) {
            ProfileService profileService = getProfileService();
            if (profileService != null) {
                Profile overlay = profileService.getOverlayProfile(profile);
                return new ProfileResource(this, overlay);
            }
        }
        return null;
    }

    @GET
    @Path("requirements")
    public ProfileRequirements requirements() {
        FabricRequirements requirements = getFabricService().getRequirements();
        if (requirements != null) {
            return requirements.getOrCreateProfileRequirement(profile.getId());
        }
        return null;
    }

    @POST
    @Path("requirements")
    public void setRequirements(ProfileRequirements profileRequirements) throws IOException {
        FabricService service = getFabricService();
        FabricRequirements requirements = service.getRequirements();
        if (requirements != null) {
            requirements.addOrUpdateProfileRequirements(profileRequirements);
            service.setRequirements(requirements);
        }
    }

    @GET
    @Path("fileNames")
    public java.util.Map<String, String> fileNames() {
        Set<String> fileNames = profile.getConfigurationFileNames();
        return mapToLinks(fileNames, "/file/");
    }


    @GET
    @Path("file/{fileName: .*}")
    public Response file(@PathParam("fileName") String fileName) {
        byte[] bytes = profile.getFileConfiguration(fileName);
        if (bytes == null) {
            return Response.status(Response.Status.NOT_FOUND).
                    entity("No file: " + fileName +
                            " for profile: " + profile.getId() +
                            " version: " + profile.getVersion()).build();
        }
        String mediaType = guessMediaType(fileName);
        return Response.ok(bytes, mediaType).build();
    }

    public static String guessMediaType(String fileName) {
        // TODO isn't there a helper method in jaxrs/cxf/somewhere to do this?
        if (fileName.endsWith(".xml")) {
            return "application/xml";
        }
        if (fileName.endsWith(".wadl")) {
            return "application/wadl+xml";
        }
        if (fileName.endsWith(".wsdl")) {
            return "application/wsdl+xml";
        }
        if (fileName.endsWith(".xsd")) {
            return "application/xsd+xml";
        }
        if (fileName.endsWith(".json")) {
            return "application/json";
        }
        if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
            return "application/html";
        }
        if (fileName.endsWith(".properties")) {
            return "text/x-java-properties";
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        if (fileName.endsWith(".gif")) {
            return "image/gif";
        }
        if (fileName.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "text/plain";
    }

}
