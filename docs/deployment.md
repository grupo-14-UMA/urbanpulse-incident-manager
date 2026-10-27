# Guía de despliegue

Cómo construir, publicar y desplegar UrbanPulse. El entorno de desarrollo está en [development.md](development.md).

## Entornos

| Entorno | Dónde | Estado |
| --- | --- | --- |
| Local | Docker Compose en el portátil | Entrega 1 |
| Producción | AWS <!-- TODO: servicio (EC2, ECS, RDS...) --> | Pendiente |
<!-- TODO: añadir staging/pruebas si se usa. -->

La misma imagen se usa en todos los entornos; solo cambia la configuración externa (variables de entorno).

## Requisitos

- Docker y Docker Compose
- Acceso al registro de imágenes <!-- TODO: Docker Hub, GitHub Container Registry, Amazon ECR... -->
- Acceso al entorno de despliegue <!-- TODO: credenciales de AWS, kubectl, etc. -->

## Versionado

Cada entrega se publica con una etiqueta y una release de GitHub siguiendo [SemVer](https://semver.org/lang/es/): `v0.1.0` para la Entrega 1, `v0.2.0` para la siguiente, etc. La imagen Docker usa la misma versión.

<!-- TODO: quién crea la release (Maintainer) y cómo. -->

## Construir la aplicación

```bash
git switch main
git pull
cd src/backend
./mvnw verify
```

## Imagen Docker del backend

<!-- TODO: nombre real de la imagen y del registro. -->

```bash
docker build -t <registro>/urbanpulse-backend:0.1.0 src/backend
docker run --rm -p 8080:8080 --env-file .env <registro>/urbanpulse-backend:0.1.0
docker push <registro>/urbanpulse-backend:0.1.0
```

Se usan versiones explícitas, no solo `latest`.

## Configuración y secretos

| Variable | Descripción |
| --- | --- |
| <!-- TODO --> | <!-- TODO --> |

Las credenciales no se guardan en Git ni en la imagen. <!-- TODO: dónde se guardan en producción (GitHub Secrets, AWS Secrets Manager...). -->

## Despliegue local con Docker Compose

```bash
docker compose pull
docker compose up -d
docker compose ps
```

## Despliegue en la nube

<!-- TODO: pasos reales en AWS cuando se decida la plataforma (registrar un ADR). Si se usa Kubernetes: Deployment, rolling update (kubectl set image / rollout status) y rollback (kubectl rollout undo). -->

## Aplicación móvil

<!-- TODO: cómo se distribuye la app (Expo Go con la URL del backend desplegado, EAS Build...). -->

## Comprobaciones tras el despliegue

- [ ] El backend responde y Swagger UI carga.
- [ ] El backend conecta con la base de datos.
- [ ] Los endpoints principales responden (`GET /api/incidents`, `GET /api/categories`).
- [ ] No hay errores críticos en los logs.
- [ ] <!-- TODO: health check (por ejemplo `/actuator/health`) si se añade Spring Boot Actuator. -->

## Rollback

<!-- TODO: cómo volver a la versión anterior (redesplegar la imagen de la versión previa). -->

## CI/CD

<!-- TODO: describir el workflow de GitHub Actions (#19) y, si se automatiza, el despliegue. -->
