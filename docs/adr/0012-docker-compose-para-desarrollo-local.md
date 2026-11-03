---
status: accepted
---

# ADR-0012: Docker Compose para el desarrollo local

## Contexto

El backend necesita PostgreSQL con la extensión PostGIS. El equipo trabaja con distintos sistemas operativos y es reducido, por lo que no puede dedicar tiempo a que cada persona instale y configure la base de datos a mano. Además, conviene que todos usen el mismo motor y la misma versión, y que sean los mismos que en las pruebas y, más adelante, en el despliegue en AWS.

## Opciones consideradas

1. Docker Compose
2. Instalar PostgreSQL y PostGIS en cada máquina
3. Una base de datos compartida en la nube para desarrollo

## Decisión

Utilizar Docker Compose para levantar el entorno local.

- El servicio de base de datos usa la imagen `postgis/postgis`, la misma que utilizan las pruebas con Testcontainers.
- Los datos se conservan en un volumen, de modo que `docker compose down` no los borra.
- La configuración (base de datos, usuario, contraseña y puertos) viene de un fichero `.env` que no se versiona; `.env.example` muestra el formato.
- Se puede arrancar todo el sistema con `docker compose up -d` o solo la base de datos con `docker compose up -d db` para ejecutar el backend desde el IDE con `./mvnw spring-boot:run`.
- El esquema lo crea Flyway al arrancar el backend.
- La aplicación móvil queda fuera de Compose: se ejecuta con Expo Go y se conecta al backend mediante `EXPO_PUBLIC_API_URL`.

Compose se usa solo para desarrollo local. La plataforma de producción en AWS se decidirá en un ADR aparte.

## Consecuencias

- \+ Cualquier miembro del equipo tiene el entorno listo con un solo comando, sin instalar PostgreSQL ni PostGIS.
- \+ Todos trabajan con el mismo motor que las pruebas y el despliegue, lo que evita diferencias entre entornos.
- \+ Se puede empezar de cero borrando el volumen y dejando que Flyway recree el esquema.
- − Exige tener Docker instalado y en ejecución, y consume memoria y disco en el portátil.
- − La aplicación móvil no está contenedorizada, por lo que hay que arrancarla aparte.
- − Compose no equivale a la orquestación de producción, así que el despliegue en AWS requerirá su propia configuración.