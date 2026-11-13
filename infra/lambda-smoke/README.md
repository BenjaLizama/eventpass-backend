# Prueba local de SQS y Lambda

Con Docker Desktop activo y el puerto 4568 libre, ejecutar desde la raíz del repositorio.

```powershell
pwsh -File infra/lambda-smoke/test-local-lambda.ps1
```

El script arranca Floci 2.4.0 en un proyecto de Compose temporal con acceso por loopback. Docker puede descargar la imagen de Floci y el runtime de Lambda de Node.js 22. El emulador usa el socket de Docker para iniciar el contenedor de la función.

La prueba encola un identificador de orden de prueba. La función falla intencionalmente en la primera entrega y registra la siguiente en S3 emulado. El script compara el identificador de la orden y el del mensaje y exige que SQS reporte al menos dos entregas. Al terminar elimina los recursos de la prueba y el archivo ZIP temporal. Todos los recursos AWS de esta prueba se ejecutan localmente.

La función es una prueba del mapeo SQS → Lambda y de la red entre contenedores. La emisión y la devolución de EventPass se validan con sus propias pruebas.
