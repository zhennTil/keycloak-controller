package com.kiwigrid.keycloak.controller.clientScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionSpec;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionStatus;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.fabric8.kubernetes.client.CustomResourceList;

@SuppressWarnings("serial")
@lombok.Getter
@lombok.Setter
@lombok.EqualsAndHashCode(of = "spec", callSuper = false)
public class ClientScopeResource extends CustomResource {

	public static final CustomResourceDefinition DEFINITION = new CustomResourceDefinitionBuilder()
			.withNewSpec()
			.withScope("Namespaced")
			.withGroup("k8s.kiwigrid.com")
			.withVersion("v1beta2")
			.withNewNames()
			.withKind("KeycloakClientScope")
			.withSingular("keycloakclientscope")
			.withPlural("keycloakclientscopes")
			.withShortNames("kccs")
			.endNames()
			.endSpec().build();

	private ClientScopeResourceSpec spec = new ClientScopeResourceSpec();
	private ClientScopeResourceStatus status = new ClientScopeResourceStatus();

	@lombok.Getter
	@lombok.Setter
	@lombok.EqualsAndHashCode(callSuper = false)
	public static class ClientScopeResourceSpec extends CustomResourceDefinitionSpec {

		private String keycloak = "default";
		private String realm;
		private String name;
		private Boolean includeInTokenScope;
		private Boolean displayOnConsentScreen;
		private String consentScreenText;
		private Integer guiOrder;
		private List<ClientScopeMapper> mappers = new ArrayList<>();
	}

	@lombok.Getter
	@lombok.Setter
	@lombok.EqualsAndHashCode
	public static class ClientScopeMapper {

		private String name;
		private String protocolMapper;
		private Map<String, String> config;
	}

	@lombok.Getter
	@lombok.Setter
	public static class ClientScopeResourceStatus extends CustomResourceDefinitionStatus {

		private String timestamp;
		private String error;
	}

	public static class ClientScopeResourceList extends CustomResourceList<ClientScopeResource> {}

	public static class ClientScopeResourceDoneable extends CustomResourceDoneable<ClientScopeResource> {
		public ClientScopeResourceDoneable(
                ClientScopeResource resource, Function<ClientScopeResource, ClientScopeResource> function) {
			super(resource, function);
		}
	}
}