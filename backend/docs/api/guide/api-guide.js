"use strict";

const baseUrlInput = document.querySelector("#api-base-url");
const swaggerTags = {
  "signupLocalAccount": "Auth",
  "loginLocalAccount": "Auth",
  "listInstitutions": "Institution",
  "applyInstructor": "Instructor",
  "listMyInstructorApplications": "Instructor",
  "approveInstructorApplication": "Instructor",
  "createCourse": "Course",
  "assignCourseInstructor": "Instructor",
  "createCourseSession": "Course Session",
  "createEnrollment": "Enrollment",
  "getEnrollment": "Enrollment",
  "approveEnrollment": "Enrollment",
  "issueAttendanceQrToken": "Attendance",
  "recordAttendance": "Attendance",
  "adjustAttendance": "Attendance",
  "evaluateCompletion": "Completion",
  "confirmCompletion": "Completion",
  "getCompletion": "Completion",
  "issueCredential": "Credential",
  "getCredential": "Credential",
  "listCurrentUserCredentials": "Credential",
  "downloadCredentialDocument": "Credential",
  "verifyCredential": "Credential",
  "revokeCredential": "Credential",
  "reissueCredential": "Credential"
};

document.querySelectorAll(".api-step").forEach((step) => {
  const operationId = step.dataset.operationId;
  const swaggerTag = swaggerTags[operationId];
  const link = document.createElement("a");
  link.className = "swagger-link";
  link.href = `/swagger-ui/index.html#/${encodeURIComponent(swaggerTag)}/${operationId}`;
  link.target = "_blank";
  link.rel = "noopener";
  link.textContent = "Swagger에서 열기";
  step.insertBefore(link, step.querySelector("pre"));
});

document.querySelectorAll("[data-copy-curl]").forEach((button) => {
  button.addEventListener("click", async () => {
    const code = button.closest(".api-step").querySelector("pre code");
    const baseUrl = baseUrlInput.value.trim().replace(/\/$/, "");
    const command = code.textContent.replaceAll("{baseUrl}", baseUrl);

    await navigator.clipboard.writeText(command);
    button.textContent = "복사됨";
    window.setTimeout(() => {
      button.textContent = "curl 복사";
    }, 1200);
  });
});
