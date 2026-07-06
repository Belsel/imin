.PHONY: help db backend frontend dev

help: ## Print available targets with descriptions
	@echo ""
	@echo "Available targets:"
	@echo "  help      Print this help message (default)"
	@echo "  db        Start the local Postgres container (imin-db)"
	@echo "  backend   Export env vars and run the Spring Boot backend"
	@echo "  frontend  Run the Vite dev server for the frontend"
	@echo "  dev       Start db, backend, and frontend each in its own window"
	@echo ""

db: ## Start the local Postgres Docker container
	docker start imin-db

backend: ## Export env vars and run the Spring Boot backend
	cd backend && \
	export DATABASE_URL=jdbc:postgresql://localhost:5432/imin && \
	export DATABASE_USERNAME=postgres && \
	export DATABASE_PASSWORD=postgres && \
	export JWT_SECRET=change-me-to-a-long-random-value && \
	export GOOGLE_CLIENT_ID=placeholder-not-a-real-client-id && \
	export GOOGLE_CLIENT_SECRET=placeholder-not-a-real-client-secret && \
	export FRONTEND_URL=http://localhost:5173 && \
	export RESEND_API_KEY= && \
	export EMAIL_FROM_ADDRESS=no-reply@example.com && \
	export ORS_API_KEY= && \
	export PORT=8080 && \
	./mvnw.cmd spring-boot:run

frontend: ## Run the Vite dev server
	cd frontend && npm run dev

dev: ## Start db, backend, and frontend each in its own window
	start "imin-db"       $(MAKE) db
	start "imin-backend"  $(MAKE) backend
	start "imin-frontend" $(MAKE) frontend
