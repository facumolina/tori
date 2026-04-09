import sys
import time
import os
import pandas as pd

# Defects4J related vars
d4j_home = os.getenv('DEFECTS4J_HOME')
d4j_projects_dir = d4j_home + "/framework/projects"

# Subjects csv
subjects_csv = "tests-d4j/defects4j-subjects.csv"
subjects_df = pd.read_csv(subjects_csv)


# Get args
identifier = sys.argv[1]
bid = sys.argv[2]

# Read from csv
subject_row = subjects_df[(subjects_df["identifier"] == identifier) & (subjects_df["bug"] == int(bid))].iloc[0]

base_dir = subject_row["base_dir"]
main_deps = subject_row["main_deps"]
test_classes_path = subject_row["tests_deps"]
extra_classes = subject_row["extra_targets"]

# Get the target info
proj_folder = d4j_projects_dir + "/" + identifier
target_classes_file = proj_folder + "/modified_classes/" + str(bid) + ".src"
target_class = open(target_classes_file).read().splitlines()[0]
cloned_dir = d4j_home + "/" + base_dir

sources_dir = cloned_dir + "/" + subject_row["main_sources"]
tests_dir = cloned_dir + "/" + subject_row["test_sources"]

def build_classpath(subject_base_dir,main_dep,test_classes_path):
    # Split main dep in char ':' and join each part with subject_base_dir
    subject_cp = ':'.join([os.path.join(subject_base_dir, dep) for dep in main_dep.split(':')])
    subject_cp = subject_cp+':'+test_classes_path
    return subject_cp

# Build the classpath
classpath = build_classpath(cloned_dir,main_deps,test_classes_path)


all_test_methods_set = set()
all_tests_file = cloned_dir + "/all_tests"
with open(all_tests_file) as f:
    all_tests_cases = f.read().splitlines()
    for test in all_tests_cases:
        # tests format: testOneArgNull(org.apache.commons.lang3.AnnotationUtilsTest)
        test_class_name = test.split('(')[1].split(')')[0]
        test_method_name = test.split('(')[0]
        all_test_methods_set.add(test_class_name+"#"+test_method_name)

print()
# Replace $ by \$ in target_class
target_class = target_class.replace('$','\$')
print("target class: ", target_class)
print("total test cases: ", len(all_test_methods_set))

results_df = pd.DataFrame(columns=["project","bid","test_class","test_method","sfc","time"])

def build_metric_config_file(target_class, extra_classes):
    target_class_file_full_path = sources_dir + "/" + target_class.replace('.','/') + ".java"
    metric_config_content = "target_class=" + target_class_file_full_path + "\n"
    exec_level_config = "exec_level=test_method\n"
    metric_config_file = f"tests-d4j/{identifier}-{bid}_state_field_coverage.properties"
    with open(metric_config_file, 'w') as f:
        f.write(metric_config_content)
        f.write(exec_level_config)

    print("Metric config file content:")
    print(metric_config_content)

metric_config_file = f"tests-d4j/{identifier}-{bid}_state_field_coverage.properties"
if not os.path.exists(metric_config_file):
    build_metric_config_file(target_class, extra_classes)

# Call covrep for each test class
for test in all_test_methods_set:
    print()
    print("---- Running Tori for test: ", test, " ----")

    # Run tori
    test_class = test.split("#")[0]
    test_method = test.split("#")[1]

    start_time = time.time()
    full_test_class_path = tests_dir + "/" + test_class.replace('.','/') + ".java"
    cmd = "java -jar build/libs/tori-1.0.0-all.jar -t " + full_test_class_path + " -m " + test_method + " -metric org.tori.metrics.StateFieldCoverage -metric-config " + metric_config_file
    print("cmd: ", cmd)
    output = os.popen(cmd).read()
    # look for the line state_field_coverage_score: value in the output
    sfc_score = 0.0
    for line in output.splitlines():
        if "state_field_coverage_score:" in line:
            sfc_score = float(line.split("state_field_coverage_score:")[1].strip())
            break

    end_time = time.time()

    # Print output
    print("output:", output)
    print("sfc score:", sfc_score)

    time_spent = round(end_time - start_time, 2)
    
    new_row = {"project": identifier, "bid": bid, "test_class": test_class, "test_method": test_method, "sfc": sfc_score, "time": time_spent}
    results_df = pd.concat([results_df, pd.DataFrame([new_row])])
    
    
results_csv =  "tests-d4j/" + identifier + "_" + bid + "_sfc_results.csv"
results_df.to_csv(results_csv, index=False)
print("Results saved to: ", results_csv)
print()
print("Done!")