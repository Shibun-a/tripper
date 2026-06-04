# Tripper: Embabel Travel Planner Agent

![Build](https://github.com/embabel/embabel-agent/actions/workflows/maven.yml/badge.svg)

<div >

![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)
![Spring](https://img.shields.io/badge/spring-%236DB33F.svg?style=for-the-badge&logo=spring&logoColor=white)
![Apache Tomcat](https://img.shields.io/badge/apache%20tomcat-%23F8DC75.svg?style=for-the-badge&logo=apache-tomcat&logoColor=black)
![Apache Maven](https://img.shields.io/badge/Apache%20Maven-C71A36?style=for-the-badge&logo=Apache%20Maven&logoColor=white)
![ChatGPT](https://img.shields.io/badge/chatGPT-74aa9c?style=for-the-badge&logo=openai&logoColor=white)
![JSON](https://img.shields.io/badge/JSON-000?style=for-the-badge&logo=json&logoColor=fff)
![htmx](https://img.shields.io/badge/htmx-3366CC.svg?style=for-the-badge&logo=htmx&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/github%20actions-%232671E5.svg?style=for-the-badge&logo=githubactions&logoColor=white)
![Docker](https://img.shields.io/badge/docker-%230db7ed.svg?style=for-the-badge&logo=docker&logoColor=white)
![IntelliJ IDEA](https://img.shields.io/badge/IntelliJIDEA-000000.svg?style=for-the-badge&logo=intellij-idea&logoColor=white)

</div>

---

<table>
<tr>
<td width="200">
<img src="https://github.com/embabel/embabel-agent/blob/main/embabel-agent-api/images/315px-Meister_der_Weltenchronik_001.jpg?raw=true" width="180" alt="Embabel Agent">
</td>
<td>

**Tripper** is a travel planning agent that helps you create personalized travel itineraries,
based on your preferences and interests. It uses web search, mapping and integrates with Airbnb.
It demonstrates the power of the [Embabel agent framework](https://www.github.com/embabel/embabel-agent).

**Key Features:**

- 🤖 Demonstrates Embabel's core concepts of deterministic planning and centering agents around a domain model
- 🌍 Illustrates the use of multiple LLMs (Claude Sonnet, GPT-4.1-mini) in the same application
- 🗺️ Extensive use of MCP tools for mapping, image and web search, wikipedia and Airbnb integration
- 📱 Modern web interface with htmx
- 🐳 Docker containerization for MCP tools
- 🚀 CI/CD with GitHub Actions

</td>
</tr>
</table>

## Portfolio Extension Note

This fork starts from the open-source Embabel Tripper project. The current goal is to turn the baseline demo into a stronger AI application portfolio project by adding reproducibility, architecture documentation, tests, and later RAG/evaluation/observability features.

Personal extension docs:

- [Project Note](PROJECT-NOTE.md)
- [Local Development Guide](LOCAL-DEVELOPMENT.md)
- [Architecture Guide](infra.md)
- [AI Application Plan](README-AI-APPLICATION-PLAN.md)

## 🚀 Quick Start

> Warning: Tripper is a genuinely useful travel planner. But be aware that its extensive LLM usage will cost money. A
> typical run costs around $0.10c.

### Prerequisites

- Java 21+
- Docker Desktop, if you want MCP tools or Docker-based local services
- Maven is optional. This repository includes Maven Wrapper, so use `./mvnw`.
- Docker Model Runner is only needed if you use the Docker Model Runner compose file:
  ```bash
  docker desktop enable model-runner --tcp=12434
  ```

### Environment Setup

1. **Configure API Keys**
   ```bash
   export OPENAI_API_KEY=your_openai_api_key_here
   # Set your Brave API key for image search
   export BRAVE_API_KEY=your_brave_api_key_here
   ```

   For a homepage-only smoke test, non-empty dummy values are enough. A real planning run requires valid keys.

   ```bash
   export OPENAI_API_KEY=dummy
   export BRAVE_API_KEY=dummy
   export GOOGLE_CLIENT_ID=dummy
   export GOOGLE_CLIENT_SECRET=dummy
   ```

2. **Set MCP Environment variables** for MCP tools running in Docker
   ```bash
   # Copy the example environment file
   cp mcp.env.example .mcp.env
   
   # Edit mcp.env with your configuration
   nano .mcp.env
   ```

### Running the Application

1. **Start Background Services** (optional, needed for MCP tool-backed runs)
   ```bash
   docker compose up mcp-gateway zipkin
   ```

2. **Launch the Travel Planner**

   **Option A: Using Shell Script**
   ```bash
   ./run.sh
   ```

   **Option B: Using Maven Wrapper**
   ```bash
   ./mvnw -Dmaven.test.skip=true spring-boot:run
   ```

   **Option C: Using IDE**
    - Open the project in your IDE
    - Run it in the way your IDE runs Spring Boot apps. In IntelliJ IDEA, simply run the main method in
      `TripperApplication.kt`.

3. **Access the Application**
    - Travel Planner: [http://localhost:8747/](http://localhost:8747/)
    - Platform Info: [http://localhost:8747/platform](http://localhost:8747/platform)

### Running the Application with Docker

1. **Launch the Travel Planner**
   ```bash
   docker compose --profile in-docker up --build
   ```

2. **Access the Application**
    - Travel Planner: [http://localhost:8747/](http://localhost:8747/)
    - Platform Info: [http://localhost:8747/platform](http://localhost:8747/platform)

> Note that the default port is `8747` not the usual Java `8080`. This is because
> we often run multiple Embabel servers at once and don't want them to conflict.
> The specific port is a reference to an [iconic aircraft](https://en.wikipedia.org/wiki/Boeing_747).
> It's easy to change the port in `application.yml`.

### Setup OAuth Credentials

Enable security by changing the following line in `application.yml`:

```properties
embabel.security.enabled=true
```

Then follow these steps to set up Google OAuth:

1. Get Google OAuth credentials from [Google Cloud Console](https://console.cloud.google.com/)
2. Add redirect URI: `http://localhost:8747/login/oauth2/code/google`
3. Set your `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` environment variables:
   ```bash
   export GOOGLE_CLIENT_ID=your_google_client_id_here
   export GOOGLE_CLIENT_SECRET=your_google_client_secret_here
   ```

For more details, see the [Security Guide](README-SECURITY.md).

## 📸 Screenshots

<div align="center">

### Itinerary Input

<img src="images/input1.jpg" alt="Travel Planner Input Interface" width="600"/>

*Input form for travel preferences*

### Generated Itinerary

<img src="images/output1.jpg" alt="Travel Planner Output" width="600"/>

*AI-generated travel itinerary with detailed recommendations*

### Link to Interactive Map

<img src="images/map.jpg" alt="Interactive map" width="600"/>

*Map link included in output*

### Link to Airbnb

<img src="images/airbnb.jpg" alt="Airbnb" width="600"/>

*Airbnb links for each stay of the trip*

### Plan and Usage Information

<img src="images/plan.jpg" alt="Plan and usage" width="600"/>

*Information about plan and usage, including total cost*

### Event Stream

<img src="images/process.jpg" alt="Events" width="600"/>

*Emits events about process flow*

</div>

## 🏗️ Architecture

The Tripper agent follows a modern microservices architecture:

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Frontend      │    │   Spring Boot/  │    │      LLMs       │
│   (htmx)        │◄──►│ Embabel Backend │◄──►│ (Claude,GPT 4)  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                │
                                ▼
                       ┌─────────────────┐
                       │   Docker        │
                       │   MCP tools     │
                       └─────────────────┘
```

**Components:**

- **Frontend**: Modern web interface built with htmx for seamless interactions
- **Backend**: Kotlin-based Spring Boot application handling business logic. Key flow is defined in `TripperAgent.kt`.
- **LLMs**: Illustrates use of multiple LLMs
- **Containerization**: Docker for consistent deployment across environments and MCP tool management

## 🛠️ Development

### Tech Stack

- **Backend**: Kotlin, Embabel, Spring Boot, Apache Tomcat
- **Frontend**: htmx, JSON APIs
- **Build**: Apache Maven Wrapper
- **DevOps**: Docker, GitHub Actions

### Note For Linux Developers

- Ensure proper software version: Docker Desktop 4.43.1
- Linux Docker Desktop does not support yet Model Runner in GUI. Please
  follow [Model Runner Documentation](https://docs.docker.com/ai/model-runner/)
- Validation step:

 ```bash
   docker model pull  jimclark106/all-minilm:23M-F16
```

* Thereafter below *compose* would not be required (due to temparary lack of support on Linux):

 ```bash
   docker compose --file compose.dmr.yaml up
```

### Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the Apache License - see the [LICENSE](LICENSE) file for details.

## 🤝 Support

For questions, issues, or contributions, please visit our [GitHub repository](https://github.com/embabel/embabel-agent)
or open an issue.

## Contributors

[![Embabel contributors](https://contrib.rocks/image?repo=embabel/tripper)](https://github.com/embabel/tripper/graphs/contributors)


---

<div align="center">

(c) Embabel 2025

[🌐 Website](https://embabel.com) • [📧 Contact](mailto:info@embabel.com) • [🐦 Twitter](https://twitter.com/springrod)
