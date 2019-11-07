package com.kiwigrid.keycloak.controller.clientScope;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Singleton;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;

import static org.keycloak.models.ClientScopeModel.INCLUDE_IN_TOKEN_SCOPE;
import static org.keycloak.models.ClientScopeModel.DISPLAY_ON_CONSENT_SCREEN;
import static org.keycloak.models.ClientScopeModel.CONSENT_SCREEN_TEXT;
import static org.keycloak.models.ClientScopeModel.GUI_ORDER;

import com.kiwigrid.keycloak.controller.KubernetesController;
import com.kiwigrid.keycloak.controller.keycloak.KeycloakController;

import io.fabric8.kubernetes.client.KubernetesClient;

@Singleton
public class ClientScopeController extends KubernetesController<ClientScopeResource> {

	final KeycloakController keycloak;

	public ClientScopeController(KeycloakController keycloak, KubernetesClient kubernetes) {
		super(kubernetes, ClientScopeResource.DEFINITION, ClientScopeResource.class, ClientScopeResource.ClientScopeResourceList.class,
               ClientScopeResource.ClientScopeResourceDoneable.class);
		this.keycloak = keycloak;
	}

	@Override
	public void apply(ClientScopeResource clientScopeResource) {

		var keycloak = clientScopeResource.getSpec().getKeycloak();
		var realm = clientScopeResource.getSpec().getRealm();
		var clientScopeName = clientScopeResource.getSpec().getName();

		// get realm resource

		var optionalRealm = realm(keycloak, realm);
		if (optionalRealm.isEmpty()) {
			log.warn("{}/{}/client-scopes{}: creating client scope failed because realm was not found", keycloak, realm, clientScopeName);
			updateStatus(clientScopeResource, "Realm not found");
			return;
		}
		var realmResource = optionalRealm.get();

		// process client scope

		try {

			String clientScopeUuid;

			// handle client scope basics

			Optional<ClientScopeRepresentation> maybeClientScope =
					realmResource.clientScopes().findAll().stream().filter(s -> s.getName().equals(clientScopeName)).findFirst();
			if (!maybeClientScope.isPresent()) {
				var clientScope = new ClientScopeRepresentation();
				clientScope.setProtocol("openid-connect");
				clientScope.setName(clientScopeName);
				mapAttributes( true, clientScopeResource.getSpec(), clientScope);
				clientScopeUuid = getId(realmResource.clientScopes().create(clientScope));
				log.info("{}/{}/client-scopes/{}: created client scope", keycloak, realm, clientScopeName);
			} else {
				var clientScope = maybeClientScope.get();
				clientScopeUuid = clientScope.getId();
				if ( mapAttributes( false, clientScopeResource.getSpec(), clientScope)) {
					realmResource.clientScopes().get(clientScopeUuid).update(clientScope);
					log.info("{}/{}/client-scopes/{}: updated client scope", keycloak, realm, clientScopeName);
				}
			}

			// handle other resources
			manageMappers(realmResource, clientScopeUuid, clientScopeResource);

			updateStatus(clientScopeResource, null);
		} catch (RuntimeException e) {
			String error = e.getClass().getSimpleName() + ": " + e.getMessage();
			if (e instanceof WebApplicationException) {
				var response = WebApplicationException.class.cast(e).getResponse();
				error = "Keycloak returned " + response.getStatus() + " with: " + response.readEntity(String.class);
			}
			log.error(keycloak + "/" + realm + "/client-scopes/" + clientScopeName + ": " + error);
			updateStatus(clientScopeResource, error);
		}
	}

	@Override
	public void delete(ClientScopeResource clientScopeResource) {

		var keycloak = clientScopeResource.getSpec().getKeycloak();
		var realm = clientScopeResource.getSpec().getRealm();
		var clientScopeName = clientScopeResource.getSpec().getName();

		// get realm resource

		var optionalRealm = realm(keycloak, realm);
		if (optionalRealm.isEmpty()) {
			log.warn("{}/{}: deleting client scope {} failed because realm was not found", keycloak, realm, clientScopeName);
			return;
		}
		var clientScopesResource = optionalRealm.get().clientScopes();

		// delete client scope

		try {
			clientScopesResource.get(clientScopeName).remove();
			log.info("{}/{}: {} client scope deleted", keycloak, realm, clientScopeName);
		} catch (NotFoundException e) {
			log.info("{}/{}: {} client scope not found, nothing to delete", keycloak, realm, clientScopeName);
		}
	}

	@Override
	public void retry() {
		customResources.list().getItems().stream()
				.filter(r -> r.getStatus().getError() != null)
				.forEach(this::apply);
	}

	// internal

	void updateStatus(ClientScopeResource clientScopeResource, String error) {

		// skip if nothing changed

		if (clientScopeResource.getStatus().getTimestamp() != null && Objects.equals(clientScopeResource.getStatus().getError(), error)) {
			return;
		}

		// update status

		clientScopeResource.getStatus().setError(error);
		clientScopeResource.getStatus().setTimestamp(Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
		customResources.withName(clientScopeResource.getMetadata().getName()).replace(clientScopeResource);
	}

	Optional<RealmResource> realm(String keycloakName, String realmName) {
		return keycloak
				.get(keycloakName)
				.map(k -> k.realm(realmName))
				.filter(realm -> {
					try {
						realm.toRepresentation();
						return true;
					} catch (NotFoundException e) {
						return false;
					}
				});
	}

	boolean mapAttributes(boolean create, ClientScopeResource.ClientScopeResourceSpec spec, ClientScopeRepresentation clientScope) {
		var changed = false;

		var attributes = clientScope.getAttributes();
		if (attributes == null)
			attributes = new HashMap<String, String>();

		if (changed |= changed(create, spec, "includeInTokenScope", boolString(spec.getIncludeInTokenScope()), attributes.get(INCLUDE_IN_TOKEN_SCOPE))) {
			attributes.put(INCLUDE_IN_TOKEN_SCOPE, boolString(spec.getIncludeInTokenScope()));
		}
		if (changed |= changed(create, spec, "displayOnConsentScreen", boolString(spec.getDisplayOnConsentScreen()), attributes.get(DISPLAY_ON_CONSENT_SCREEN))) {
			attributes.put(DISPLAY_ON_CONSENT_SCREEN, boolString(spec.getDisplayOnConsentScreen()));
		}
		if (changed |= changed(create, spec, "consentScreenText", spec.getConsentScreenText(), attributes.get(CONSENT_SCREEN_TEXT))) {
			attributes.put(CONSENT_SCREEN_TEXT, spec.getConsentScreenText());
		}
		var guiOrder = spec.getGuiOrder() == null ? null : spec.getGuiOrder().toString();
		if (changed |= changed(create, spec, "guiOrder", guiOrder, attributes.get(GUI_ORDER))) {
			attributes.put(GUI_ORDER, guiOrder);
		}

		if (changed) {
			clientScope.setAttributes(attributes);
		}

		return changed;
	}

	String boolString(Boolean bool) {
		if (bool == null)
			return null;

		return bool.equals( Boolean.TRUE ) ? "true" : "false";
	}

	boolean changed(boolean create, ClientScopeResource.ClientScopeResourceSpec spec, String name, Object specValue, Object clientScopeValue) {
		boolean changed = specValue != null && !specValue.equals(clientScopeValue);
		if (changed) {
			if (create) {
				log.debug("{}/{}/client-scopes/{}: set {} to {}",
						  spec.getKeycloak(), spec.getRealm(), spec.getName(), name, specValue);
			} else {
				log.info("{}/{}/client-scopes/{}: change {} from {} to {}",
						 spec.getKeycloak(), spec.getRealm(), spec.getName(), name, clientScopeValue, specValue);
			}
		}
		return changed;
	}

	void manageMappers(RealmResource realmResource, String clientScopeUuid, ClientScopeResource clientScopeResource) {

		var keycloak = clientScopeResource.getSpec().getKeycloak();
		var realm = clientScopeResource.getSpec().getRealm();
		var clientScopeName = clientScopeResource.getSpec().getName();

		var mappersResource = realmResource.clientScopes().get(clientScopeUuid).getProtocolMappers();
		var mappers = mappersResource.getMappers();
		var specMappers = clientScopeResource.getSpec().getMappers();

		// handle requested mappers

		for (var specMapper : specMappers) {
			var mapperName = specMapper.getName();
			var optional = mappers.stream().filter(m -> m.getName().equals(mapperName)).findFirst();
			if (optional.isEmpty()) {
				var keycloakMapper = new ProtocolMapperRepresentation();
				keycloakMapper.setName(mapperName);
				keycloakMapper.setProtocol("openid-connect");
				keycloakMapper.setProtocolMapper(specMapper.getProtocolMapper());
				keycloakMapper.setConfig(specMapper.getConfig());
				getId(mappersResource.createMapper(keycloakMapper));
				log.info("{}/{}/client-scopes/{}: created mapper {}", keycloak, realm, clientScopeName, mapperName);
			} else {
				var keycloakMapper = optional.get();
				if (keycloakMapper.getConfig().equals(specMapper.getConfig())
						&& keycloakMapper.getProtocolMapper().equals(specMapper.getProtocolMapper())) {
					continue;
				}
				keycloakMapper.setProtocolMapper(specMapper.getProtocolMapper());
				keycloakMapper.setConfig(specMapper.getConfig());
				mappersResource.update(keycloakMapper.getId(), keycloakMapper);
				log.info("{}/{}/client-scopes/{}: updated mapper {}", keycloak, realm, clientScopeName, mapperName);
			}
		}

		// remove obsolete mappers

		var names = specMappers.stream().map(ClientScopeResource.ClientScopeMapper::getName).collect(Collectors.toSet());
		for (var mapper : mappers) {
			if (!names.contains(mapper.getName())) {
				mappersResource.delete(mapper.getId());
				log.info("{}/{}/client-scopes/{}: deleted obsolete mapper {}", keycloak, realm, clientScopeName, mapper.getName());
			}
		}
	}

	String getId(Response response) {
		if (response.getStatus() >= 400) {
			throw new IllegalStateException("Failed to get id from response because status was " + response.getStatus()
					+ " and response: " + response.readEntity(String.class));
		}
		return Stream.of(response.getHeaderString(HttpHeaders.LOCATION).split("/"))
				.filter(p -> p.length() == 36)
				.filter(p -> p.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
				.findAny().get();
	}
}