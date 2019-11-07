package com.kiwigrid.keycloak.controller;

import java.util.HashSet;
import java.util.Set;

import com.kiwigrid.keycloak.controller.client.ClientController;
import com.kiwigrid.keycloak.controller.clientScope.ClientScopeController;
import com.kiwigrid.keycloak.controller.keycloak.KeycloakController;
import com.kiwigrid.keycloak.controller.realm.RealmController;
import io.fabric8.kubernetes.client.Watch;
import io.micronaut.scheduling.annotation.Scheduled;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Singleton;

@Singleton
@lombok.RequiredArgsConstructor
public class ApplicationHandler {

	final Set<Watch> watches = new HashSet<>();
	final KeycloakController keycloakController;
	final RealmController realmController;
	final ClientController clientController;
	final ClientScopeController clientScopeController;

	@Scheduled(fixedRate = "${retry-rate:60s}")
	void retry() {
		keycloakController.retry();
		realmController.retry();
		clientController.retry();
		clientScopeController.retry();
	}

	@PostConstruct
	private void init() {
		watches.add(keycloakController.watch());
		watches.add(realmController.watch());
		watches.add(clientController.watch());
		watches.add(clientScopeController.watch());
	}

	@PreDestroy
	private void close() {
		watches.forEach(Watch::close);
	}
}