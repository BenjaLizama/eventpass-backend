FROM public.ecr.aws/lambda/nodejs:22 AS dependencies
COPY package.json package-lock.json ${LAMBDA_TASK_ROOT}/
RUN npm ci --omit=dev --ignore-scripts

FROM public.ecr.aws/lambda/nodejs:22
COPY --from=dependencies /var/task/node_modules ${LAMBDA_TASK_ROOT}/node_modules
COPY index.mjs process-order.mjs dependency-call.mjs ${LAMBDA_TASK_ROOT}/
USER 10001:10001
CMD ["index.handler"]
