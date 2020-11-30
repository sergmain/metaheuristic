# STEP 1 build static website
FROM nginx:alpine
## Remove default nginx website
RUN rm -rf /usr/share/nginx/html/*
## Copy website to default nginx public folder
COPY /static-files /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]