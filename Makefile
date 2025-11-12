# ===== Config =====
CONTAINER ?= podman
KUBECTL   ?= kubectl
KIND      ?= kind

APP_NAME  ?= camel-challenge
IMAGE_REG ?= ghcr.io/valoisgomes
IMAGE_TAG ?= $(shell git rev-parse --short HEAD)
IMAGE     ?= $(IMAGE_REG)/$(APP_NAME):$(IMAGE_TAG)
IMAGE_LATEST ?= $(IMAGE_REG)/$(APP_NAME):latest

NAMESPACE ?= demo
CLUSTER   ?= dev-cluster

# ===== Targets =====

## Build do jar e da imagem
build:
	./mvnw -DskipTests clean package
	$(CONTAINER) build -t $(IMAGE) -t $(IMAGE_LATEST) .

## Teste local da imagem (Ctrl+C para parar)
run:
	$(CONTAINER) run --rm -p 8080:8080 $(IMAGE_LATEST)

stop:
	-$(CONTAINER) stop $(APP_NAME) || true

## Login no GHCR (use: make login GHCR_PAT=seu_token)
login:
	echo "$$GHCR_PAT" | $(CONTAINER) login ghcr.io -u valoisgomes --password-stdin

## Push da imagem para o registry
push: build
	$(CONTAINER) push $(IMAGE)
	$(CONTAINER) push $(IMAGE_LATEST)

## Carregar imagem no Kind (sem registry)
kind-load: build
	# funciona na maioria dos casos com provider=podman
	$(KIND) load docker-image $(IMAGE_LATEST) --name $(CLUSTER) || \
	( $(CONTAINER) save $(IMAGE_LATEST) -o app.tar && $(KIND) load image-archive app.tar --name $(CLUSTER) )

## Deploy/Update no cluster
deploy:
	# garante que o namespace existe
	-$(KUBECTL) get ns $(NAMESPACE) >/dev/null 2>&1 || $(KUBECTL) apply -f k8s/namespace.yaml
	$(KUBECTL) apply -f k8s/deployment.yaml
	$(KUBECTL) apply -f k8s/service.yaml
	# troca a imagem para a tag atual (idempotente)
	$(KUBECTL) -n $(NAMESPACE) set image deploy/$(APP_NAME) $(APP_NAME)=$(IMAGE_LATEST)
	$(KUBECTL) -n $(NAMESPACE) rollout status deploy/$(APP_NAME)

## Logs dos pods
logs:
	$(KUBECTL) -n $(NAMESPACE) logs -l app=$(APP_NAME) --tail=200 -f

## Port-forward (abre acesso no localhost:8080)
port:
	$(KUBECTL) -n $(NAMESPACE) port-forward svc/$(APP_NAME) 8080:8080

## Remove recursos do app
undeploy:
	-$(KUBECTL) -n $(NAMESPACE) delete -f k8s/service.yaml --ignore-not-found
	-$(KUBECTL) -n $(NAMESPACE) delete -f k8s/deployment.yaml --ignore-not-found

## Limpa gerados locais
clean:
	./mvnw clean
	-$(CONTAINER) rmi $(IMAGE) $(IMAGE_LATEST) 2>/dev/null || true
	-rm -f app.tar || true

## Atalhos úteis
kind-up:
	# assume que a podman machine já está rodando
	export KIND_EXPERIMENTAL_PROVIDER=podman && $(KIND) create cluster --name $(CLUSTER)

kind-down:
	$(KIND) delete cluster --name $(CLUSTER)

.PHONY: build run stop login push kind-load deploy logs port undeploy clean kind-up kind-down
