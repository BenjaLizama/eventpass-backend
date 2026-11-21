# Docker Swarm para la EP3

La evaluación pide manager y worker, despliegue y escalado en Docker Swarm (IE3, IE7, IE8 e IE9). ECS del diagrama autorizado sigue siendo el destino AWS; esta práctica agrega la evidencia específica de Swarm. Son alternativas de ejecución, no se instala Swarm dentro de ECS.

## Practicar en un PC

Con Docker Desktop en modo Linux, desde la raíz del proyecto en PowerShell 7:

```powershell
./infra/swarm/test-swarm.ps1 -KeepRunning
```

Construye los cuatro Dockerfiles, crea dos motores Docker aislados, inicializa el manager, une el worker, carga las imágenes en ambos y ejecuta `docker stack deploy`. Comprueba aplicaciones y sesión, crea tres colas en Floci y escala Eventos `1 → 2 → 1`. Puedes repetir con `-SkipBuild` si no cambiaste las imágenes. Sin `-KeepRunning`, detiene los nodos al terminar y conserva sus volúmenes. El informe está en `.local/swarm/verification.json`.

Los endpoints son `http://localhost:18081` hasta `18084`. Swagger de Eventos: `http://localhost:18082/swagger-ui/index.html`. Las bases y Floci no publican puertos al PC. Solo se publica en loopback esta práctica; no se cambia el Gateway privado de AWS.

```powershell
# Ver nodos, servicios y distribución de tareas.
docker compose -p eventpass-swarm-lab -f infra/swarm/compose.lab.yaml exec manager docker node ls
docker compose -p eventpass-swarm-lab -f infra/swarm/compose.lab.yaml exec manager docker stack services eventpass
docker compose -p eventpass-swarm-lab -f infra/swarm/compose.lab.yaml exec manager docker service ps eventpass_ms-events

# Escalar y probar antes de volver al tamaño inicial.
docker compose -p eventpass-swarm-lab -f infra/swarm/compose.lab.yaml exec manager docker service scale eventpass_ms-events=2
Invoke-RestMethod 'http://localhost:18082/api/v1/events?page=0&size=1'
docker compose -p eventpass-swarm-lab -f infra/swarm/compose.lab.yaml exec manager docker service scale eventpass_ms-events=1

# Apagar conservando datos.
docker compose -p eventpass-swarm-lab -f infra/swarm/compose.lab.yaml down
```

Los nodos son contenedores privilegiados Docker-in-Docker, con discos independientes y socket Unix interno. No montan el socket Docker del PC ni publican una API Docker TCP. Se necesitan aproximadamente 5 GiB disponibles y espacio para imágenes duplicadas. La red fija `172.30.250.0/24` evita cambios de IP al reiniciar; si coincide con una VPN/red existente, ajusta las tres direcciones en `compose.lab.yaml` antes de crear los nodos.

Dos nodos en un PC no son dos servidores independientes ni toleran que se apague el PC. Si el docente exige máquinas separadas, repetir en dos hosts/VMs. El escalado mostrado es manual, no automático por carga.

## Archivos y decisiones

| Archivo | Función |
| --- | --- |
| `docker-stack.yml` | Servicios, imágenes, red overlay, volúmenes, réplicas y actualizaciones de Swarm. |
| `compose.lab.yaml` | Dos motores Docker para practicar; no contiene los microservicios. |
| `.env.example` | Valores ficticios de esta práctica; se copia a `.env`, ignorado por Git. |
| `init-queues.sh` | Comandos AWS CLI para crear tres colas en Floci, con espera de conexión. |
| `test-swarm.ps1` | Ejecuta y comprueba los pasos, también en GitHub Actions. |

Swarm recibe imágenes ya construidas. No usamos `build`, `depends_on` ni `restart`; el comportamiento se define en `deploy`. `env_file: .env` carga explícitamente el archivo, porque `docker stack deploy` no lo carga automáticamente. La red overlay conecta los nodos; el routing mesh permite entrar por el manager aunque las aplicaciones estén en el worker.

Las bases tienen una réplica fijada al nodo etiquetado `eventpass-data=true`: un volumen local no se replica al mover tareas. No escalar PostgreSQL, MongoDB o Redis con `docker service scale` como si fueran aplicaciones sin estado. El worker ejecuta las aplicaciones; las réplicas de Eventos comparten PostgreSQL y Redis. Un manager y un worker no ofrecen alta disponibilidad; se necesitan más workers, quorum de managers y una estrategia de datos.

La práctica usa MongoDB y compra síncrona con tickets publicados por SQS emulado. La compra asíncrona con Lambda, DynamoDB, API Gateway y DLQ se demuestra en el entorno Floci existente y después en AWS. No se presenta Lambda como conectada a este stack. No usar estas credenciales ni exponer estos puertos directamente a Internet.

## Comandos equivalentes en dos hosts Linux

En el manager, usando su IP privada alcanzable desde el worker:

```bash
docker swarm init --advertise-addr <IP_PRIVADA_MANAGER>
docker node update --label-add eventpass-data=true $(docker info --format '{{.Swarm.NodeID}}')
docker swarm join-token worker
```

Ejecutar el comando `docker swarm join` obtenido en el segundo host. Para agregar un manager se usa `docker swarm join-token manager`; no guardar tokens en Git ni mostrarlos en el video. Permitir entre hosts `2377/TCP`, `7946/TCP+UDP` y `4789/UDP`, nunca desde Internet. [Requisitos de red de Docker](https://docs.docker.com/engine/swarm/swarm-tutorial/).

Copiar este directorio al manager y crear `.env`. Cargar las imágenes con `docker image save`/`docker image load` en ambos hosts, o usar ECR, autenticar los hosts y reemplazar las cuatro URI de imágenes en el stack. En el manager:

```bash
docker stack deploy --with-registry-auth -c docker-stack.yml eventpass
docker node ls
docker stack services eventpass
docker service scale eventpass_ms-events=2
docker service ps eventpass_ms-events
```
