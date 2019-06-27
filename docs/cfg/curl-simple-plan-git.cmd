@rem curl -u q=1:123 -F 'plan={"code"="simple-plan-git"}' -F "planYamlAsStr=@simple-plan-git.yaml"  http://localhost:8080/rest/v1/launchpad/plan/plan-add-commit
@rem curl -u q=1:123 -H "Content-Type: application/json" -v -X POST -d "@simple-plan-create.json" -d "planYamlAsStr=@simple-plan-git.yaml" http://localhost:8080/rest/v1/launchpad/plan/plan-add-commit
@rem curl -u q=1:123 -H "Content-Type: application/json; charset=utf-8" -X POST -d "@simple-plan-create.json" http://localhost:8080/rest/v1/launchpad/plan/plan-add-commit?planYamlAsStr=simple-plan-git
@rem curl -u q=1:123 -F "planYaml=@simple-plan-git.yaml;type=application/text" http://localhost:8080/rest/v1/launchpad/plan/plan-add-commit-form
@rem curl -u q=1:123 -F "planYaml=@simple-plan-git.yaml;type=text/plain" http://localhost:8080/rest/v1/launchpad/plan/plan-add-commit-form
@curl -u q=1:123 --data-urlencode "planYaml@simple-plan-git.yaml" http://localhost:8080/rest/v1/launchpad/plan/plan-add-commit-form
curl -u q=1:123 -F "file=@simple-plan-git.yaml"  http://localhost:8080/rest/v1/launchpad/plan/plan-upload-from-file
