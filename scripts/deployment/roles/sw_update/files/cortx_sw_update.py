#   Description:
#       This script used to perform automated cortx stack update via UI.
#   Steps:
#       1. Perform Prebording (admin user creation).
#       2. Perform Onboarding with default values (assuming this is qa testing so non default values are not executed here at this momemnt).
#       3. Upload cortx update Build.     
#       4. Executes Software Update.
#       5. Validates the Software Update.
#   
#   Note: 
#      All this sowftware update process executed on UI using selinum (Functinal testing automation).
#
#   Run command:
#         cortx_sw_update.py -ip <ip> -iso <update_iso> -un <user_name> -pw <password> -dns <dns_server> -ns <dns_name_server>
#
##########################################################################################################################################


from selenium import webdriver
from selenium.webdriver.common.desired_capabilities import DesiredCapabilities
from selenium.webdriver.common.keys import Keys
import time
import tempfile
import requests 
import shutil
import argparse
import re 

class CortxSoftwareUpdate:

    screen_count = 0
    step_name = ""
    
    def __init__(self, mgmt_ip, cortx_update_iso, admin_user, admin_pass, dns_server_domain, dns_name_server):
        options = webdriver.ChromeOptions()
        options.add_argument('--ignore-ssl-errors=yes')
        options.add_argument('--ignore-certificate-errors')
        options.add_argument('--no-sandbox')
        options.add_argument("--disable-dev-shm-usage")
        options.add_argument('--headless')

        self.driver = webdriver.Chrome("/bin/chromedriver", chrome_options=options)
       
        self.login_url = "https://{}:28100/#/login".format(mgmt_ip)
        self.preboarding_url = "https://{}:28100/#/preboarding/welcome".format(mgmt_ip)
        self.onboarding_url = "https://{}:28100/#/onboarding".format(mgmt_ip)
        self.maintanance_url = "https://{}:28100/#/maintenance/software".format(mgmt_ip)

        self.cortx_update_iso =cortx_update_iso
        self.admin_user = admin_user
        self.admin_pass = admin_pass
        self.dns_server_domain = dns_server_domain
        self.dns_name_server = dns_name_server
        
        self.admin_email = "cortx-admin@seagate.com"
        self.system_name = "cortx-devops-sw"
        self.ntp_server = "time.seagate.com"

        self.cortx_update_iso_folder = tempfile.mkdtemp()

    def preboarding(self):
        
        try:    
            self.step_name = "1_prebording"
            self.screen_count = 0
            print("{} Started".format(self.step_name))

            self.driver.get(self.preboarding_url)
            time.sleep(5)

            self.click(self.driver.find_element_by_id("welcome-startbtn"))

            self.click(self.driver.find_element_by_id("show-license-agreement-dialogbtn"))
            
            self.click(self.driver.find_element_by_id("license-acceptagreement"))
            
            self.driver.find_element_by_id("adminUsername").send_keys(self.admin_user)
            self.driver.find_element_by_id ("adminEmail").send_keys(self.admin_email)
            self.driver.find_element_by_id ("adminPassword").send_keys(self.admin_pass)
            self.driver.find_element_by_id ("confirmAdminPassword").send_keys(self.admin_pass)
            self.click(self.driver.find_element_by_id("admin-createadminuser"))
            time.sleep(60)
            self.driver.save_screenshot("{}_screen_completed.png".format(self.step_name))
            print("{} Completed".format(self.step_name))
        except Exception as error:
            self.driver.save_screenshot("{}_screen_error.png".format(self.step_name))
            print(error)
            exit(1)  

    def login(self):
        self.driver.get(self.login_url)
        self.driver.find_element_by_id("username").send_keys(self.admin_user)
        self.driver.find_element_by_id ("password").send_keys(self.admin_pass)
        self.driver.find_element_by_id("login-userbtn").click()
        time.sleep(5) 

        if len(self.driver.find_elements_by_id('Manage')) > 0:
            print("Login Success !!!")
        else:
            print("Login Failed ")
            exit(1)

    def onboarding(self):

        try:
            self.step_name = "2_onboarding" 
            self.screen_count = 0
            print("{} Started".format(self.step_name)) 

            self.driver.get(self.onboarding_url)
            time.sleep(5)
            
            # System name
            self.send_keys(self.driver.find_element_by_id("txtappliancename"), self.system_name)
            self.click(self.driver.find_element_by_class_name("white--text"))

            # SSL certificate upload
            self.click(self.driver.find_element_by_class_name("csmprimary"))

            # DNS resolver settings
            self.send_keys(self.driver.find_element_by_id("0txtDnsServer"), self.dns_name_server)
            self.send_keys(self.driver.find_element_by_id("0txtSearchDomain"), self.dns_server_domain)
            self.click(self.driver.find_element_by_class_name("white--text"))

            # Network time protocol (NTP) settings
            self.send_keys(self.driver.find_element_by_id("txtDTHostname"), self.ntp_server)
            self.click(self.driver.find_element_by_class_name("csmprimary"))

            # Notification settings
            self.click(self.driver.find_elements_by_xpath('//span[@class="cortx-ckb-tick"]')[1])
            self.click(self.driver.find_element_by_class_name("csmprimary"))

            # Onboarding summary
            self.click(self.driver.find_element_by_class_name("white--text"))

            # Confirmation
            self.click(self.driver.find_element_by_id("confirmation-dialogbox-btn"))
            time.sleep(8)
            
            self.driver.save_screenshot("{}_screen_completed.png".format(self.step_name))
            print("{} Completed".format(self.step_name))

        except Exception as error:
            self.driver.save_screenshot("{}_screen_error.png".format(self.step_name))
            print(error)

    def download_update_iso(self):
        
        local_filename = "{}/{}".format(self.cortx_update_iso_folder,self.cortx_update_iso.split('/')[-1])
        print(" Downloding cortx update ISO : {}".format(local_filename))
        r = requests.get(self.cortx_update_iso)
        with open(local_filename, 'wb') as f:
            for chunk in r.iter_content(chunk_size=1024): 
                if chunk:
                    f.write(chunk)
        self.cortx_update_iso = f.name

    def software_update(self):
       
        try:
            self.step_name = "3_software_update" 
            self.screen_count = 0
            print("{} Started".format(self.step_name))

            self.driver.get(self.maintanance_url)

            self.click(self.driver.find_element_by_id("btnInstallHotfix"))

            self.driver.find_element_by_id("file").send_keys(self.cortx_update_iso)
            time.sleep(5)
            
            self.click(self.driver.find_element_by_id("btnInstallHotfix"))
            time.sleep(300)

            self.click(self.driver.find_element_by_id("btnStartUpgrade"))
            time.sleep(300)

            self.driver.save_screenshot("{}_screen_completed.png".format(self.step_name))
            print("{} Completed".format(self.step_name))

        except Exception as error:
            self.driver.save_screenshot("{}_screen_error.png".format(self.step_name))
            print(error)
            exit(1)

    def check_status(self):
        t_end = time.time() + 60 * 45
        while time.time() < t_end:
            try:
                time.sleep(60)
                self.driver.get(self.maintanance_url)
                time.sleep(5)

                if len(self.driver.find_elements_by_id('lblUpdateHotfix')) > 0:
                    print("Session Active !!!")
                else:
                    self.login()
                    time.sleep(5)
                    self.driver.get(self.maintanance_url)
                    time.sleep(5)

                update_page = self.driver.find_element_by_class_name("cortx-last-upgrade-info-container").text
                
                if ("IN_PROGRESS" in update_page):
                    print("[ update_status ] : Software Update 'IN_PROGRESS'... : {}".format(update_page))
                elif ("FAIL" in update_page):
                    print("[ update_status ] : Software Update 'FAILED'. : {}".format(update_page))
                    self.driver.save_screenshot("{}_screen_fail.png".format(self.step_name))
                    exit(1)
                    break
                elif ("SUCCESS" in update_page):
                    print("[ update_status ] : Software Update 'SUCCESS'. : {}".format(update_page))
                    self.driver.save_screenshot("{}_screen_success.png".format(self.step_name))
                    break
            except Exception as error:
                print("[ update_status ] :Software Update UI not accessible")
        
        self.driver.save_screenshot("{}_sf_done.png".format(self.step_name))

    def send_keys(self, text_area, data):
        time.sleep(5)
        text_area.send_keys(Keys.CONTROL + "a")
        text_area.send_keys(Keys.DELETE)
        text_area.send_keys(data)
        time.sleep(5)

    def click(self, button):
        time.sleep(4)
        self.screen_count=self.screen_count+1
        self.driver.save_screenshot('{}_screen_{}.png'.format(self.step_name,self.screen_count))
        button.click()
        time.sleep(4)

    def cleanup(self):
        shutil.rmtree(self.cortx_update_iso_folder, ignore_errors=True)


############################################ INPUT ARGUMENTS ################################################################

parser = argparse.ArgumentParser(description='Software update input parameters.')
parser.add_argument('-ip', '--mgmt_ip', help='Management IP', required=True)
parser.add_argument('-iso', '--sw_update_iso', help='SW Update ISO URL', required=False)
parser.add_argument('-un', '--username', help='User Name', required=True)
parser.add_argument('-pw', '--password', help='Password', required=True)
parser.add_argument('-dns', '--dns_server_domain', help='DNS Domain', required=True)
parser.add_argument('-ns', '--name_server', help='DNS Name Server', required=True)

args = parser.parse_args()

print("--------------Provided Arguments----------------------")
print("Management IP  : {}".format(args.mgmt_ip))
print("Admin UserName : {}".format(args.username))
print("Admin Password : {}".format(args.password))
print("Update ISO     : {}".format(args.sw_update_iso))
print("DNS Server     : {}".format(args.dns_server_domain))
print("DNS Name Server: {}".format(args.name_server))
print("------------------------------------------------------")


#########################  EXECUTION BLOCK #################################################################################

# 00. Initialize the class with default,input attributes
cortx_update = CortxSoftwareUpdate(args.mgmt_ip, args.sw_update_iso, args.username, args.password, args.dns_server_domain, args.name_server)

# 01. Perform prebording
cortx_update.preboarding()

# 02. Login & Save the session for further process
cortx_update.login()

# 03. Perform onboarding
cortx_update.onboarding()

# 04. Download update iso from the provided path (http)
cortx_update.download_update_iso()

# 05. Perform 'Cortx Software Update'
cortx_update.software_update()

# 06. Cleanup the environment
cortx_update.cleanup()

# 07. validate the update status
cortx_update.check_status()

