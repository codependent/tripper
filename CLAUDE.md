# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Essential Commands

### Build and Run
- **Run application**: `./run.sh` or `mvn -Dmaven.test.skip=true spring-boot:run`
- **Run with Docker**: `docker compose --profile in-docker up`
- **Build**: `mvn clean compile`
- **Test**: `mvn test`

### Development Setup
- **Start background services**: `docker compose --file compose.yaml --file compose.dmr.yaml up`
- **Access application**: http://localhost:8080 (or http://localhost:8747 for Docker)
- **Platform info**: http://localhost:8080/platform

### Required Environment Variables
```bash
export OPENAI_API_KEY=your_openai_api_key_here
export ANTHROPIC_API_KEY=your_anthropic_api_key_here
export BRAVE_API_KEY=your_brave_api_key_here
# Optional for OAuth
export GOOGLE_CLIENT_ID=your_google_client_id_here
export GOOGLE_CLIENT_SECRET=your_google_client_secret_here
```

## Architecture Overview

**Tripper** is a travel planning agent built on the Embabel agent framework. It demonstrates deterministic planning using multiple LLMs and MCP tools.

### Core Flow (TripperAgent.kt:64-317)
1. **confirmExpensiveOperation**: Cost confirmation for travel planning
2. **findPointsOfInterest**: Uses Claude Sonnet with web/maps/math tools to find POIs
3. **researchPointsOfInterest**: Uses GPT-4.1-mini with parallel processing to research each POI
4. **proposeTravelPlan**: Uses Claude 3.7 Sonnet to create detailed HTML travel plan
5. **findPlacesToSleep**: Finds Airbnb accommodations using parallel processing
6. **postProcessHtml**: Post-processes HTML content with image styling

### Key Components

**Domain Model** (domain.kt:16-172):
- `TravelBrief` hierarchy with journey details
- `Travelers` and `Traveler` for user information
- `PointOfInterest` and research findings
- `TravelPlan` with computed Google Maps URLs

**Multi-LLM Configuration** (TripperAgent.kt:57-61):
- **Thinker**: GPT-4.1 for finding POIs
- **Researcher**: GPT-4.1-mini for parallel research
- **Writer**: Claude 3.7 Sonnet for travel plan creation

**MCP Tools Integration**:
- Docker-based MCP servers for mapping, web search, Wikipedia, Airbnb
- Brave Search API integration (Brave.kt:23-204)
- Image search and validation utilities

### Web Layer
- **Spring Boot** with Thymeleaf templates
- **htmx** for dynamic interactions
- **OAuth2 Security** with Google authentication (optional)
- Controllers in `web/` directory handle htmx requests

### Configuration
- **Main class**: `TripperApplication.kt` with `@EnableAgents`
- **Tool configuration**: `ToolsConfig.kt`
- **Properties**: Customizable personas, models, and processing limits
- **Security**: OAuth2 setup documented in README-SECURITY.md

The application uses Docker Model Runner (port 12434) and requires Java 21+ with Maven 3.6+.