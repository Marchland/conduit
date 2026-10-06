FROM gcr.io/distroless/java25-debian13

WORKDIR /app
COPY conduit.jar /app/conduit.jar

EXPOSE 8080
CMD ["/app/conduit.jar"]
