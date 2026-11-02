workspace "UrbanPulse" "Modelo C4" {

    model {
        ciudadano = person "Ciudadano" "Registra incidencias y consulta su estado"
        operador = person "Operador municipal" "Valida, prioriza y asigna incidencias"
        tecnico = person "Técnico" "Resuelve las incidencias asignadas"
        administrador = person "Administrador" "Gestiona usuarios, roles y configuración"
        analista = person "Analista" "Consulta indicadores e informes"

        sistema = softwareSystem "Sistema" "UrbanPulse: gestión inteligente de incidencias urbanas" {
            web = container "Web Ciudadanos" "Portal para reportar y seguir incidencias" "React / TypeScript"
            app = container "App Trabajadores" "Aplicación para operadores, técnicos, analistas y administradores" "React / TypeScript"
            api = container "Backend API" "Lógica de negocio y acceso a datos" "Spring Boot"
            db = container "PostgreSQL" "Almacena incidencias, usuarios y auditoría" "PostgreSQL" "Database"
        }

        datosAbiertos = softwareSystem "Datos Abiertos Málaga" "datosabiertos.malaga.eu" "Externo"
        openWeather = softwareSystem "OpenWeatherMap" "API meteorológica" "Externo"

        ciudadano -> sistema "Usa"
        operador -> sistema "Usa"
        tecnico -> sistema "Usa"
        analista -> sistema "Usa"
        administrador -> sistema "Administra"

        sistema -> datosAbiertos "Consulta datos urbanos"
        sistema -> openWeather "Consulta datos meteorológicos"

        web -> api "HTTPS / JSON"
        app -> api "HTTPS / JSON"
        api -> db "JDBC"
    }

    views {
        systemContext sistema "Contexto" {
            include *
            autolayout lr
        }

        container sistema "Contenedores" {
            include web app api db
            autolayout tb
        }

        styles {
            element "Person" {
                shape person
                background #0b4884
                color #ffffff
                stroke #073763
                strokeWidth 3
            }
            element "Software System" {
                background #d0e6fb
                color #1a1a1a
                stroke #2f7ed8
                strokeWidth 3
            }
            element "Container" {
                background #d0e6fb
                color #1a1a1a
                stroke #2f7ed8
                strokeWidth 3
            }
            element "Database" {
                background #d9f7d9
                color #1a1a1a
                stroke #3cb043
                strokeWidth 3
            }
            element "Externo" {
                background #d9f7d9
                color #1a1a1a
                stroke #3cb043
                strokeWidth 3
            }
            relationship "Relationship" {
                dashed true
                thickness 2
                color #555555
            }
        }
    }
}