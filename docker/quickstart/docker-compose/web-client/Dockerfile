FROM nginx:alpine
## Remove default nginx website
RUN rm -rf /usr/share/nginx/html/*
## From 'builder' copy website to default nginx public folder
COPY files /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]