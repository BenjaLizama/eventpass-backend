FROM node:22-alpine
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts
ADD --chmod=0444 https://truststore.pki.rds.amazonaws.com/us-east-1/us-east-1-bundle.pem /app/rds-ca.pem
COPY bootstrap-core.mjs index.mjs ./
USER node
ENTRYPOINT ["node", "index.mjs"]
