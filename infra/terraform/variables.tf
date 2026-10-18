variable "project_name" {
  description = "Short name used for resource naming conventions."
  type        = string
  default     = "devernance"
}

variable "environment" {
  description = "Deployment environment label (dev, staging, prod)."
  type        = string
  default     = "dev"
}

variable "region" {
  description = "Primary cloud region placeholder (e.g. eu-west-1, europe-west1)."
  type        = string
  default     = "eu-west-1"
}

variable "db_instance_class" {
  description = "Managed Postgres size placeholder (RDS/Cloud SQL)."
  type        = string
  default     = "db.t4g.medium"
}

variable "api_cpu" {
  description = "API service CPU units placeholder (Cloud Run / ECS)."
  type        = string
  default     = "1"
}

variable "api_memory" {
  description = "API service memory placeholder."
  type        = string
  default     = "2Gi"
}

variable "enable_object_storage" {
  description = "Whether to plan an evidence object bucket (S3/GCS)."
  type        = bool
  default     = true
}

variable "tags" {
  description = "Common tags / labels applied by real modules later."
  type        = map(string)
  default = {
    app     = "devernance"
    managed = "terraform-skeleton"
  }
}
