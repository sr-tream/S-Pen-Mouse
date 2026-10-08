#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

// Keep index 0 for recovery of the 0.1.1 journal only. Never toggle Air Actions: it controls BLE.
static const char *keys[] = {"spen_air_action", "air_cmd_with_pen_button", "spen_double_tap_launch",
    "air_button_onoff", "pen_hovering_information_preview", "pen_hovering_link_preview",
    "pen_hovering_list_scroll", "pen_hovering_pointer"};
#define COUNT (sizeof(keys)/sizeof(keys[0]))
static volatile sig_atomic_t interrupted;
static void interrupt_handler(int signo) { (void)signo; interrupted=1; }

static int command(int user,const char *op,const char *key,const char *value,char *output,size_t capacity) {
    int pipefd[2]; if(pipe2(pipefd,O_CLOEXEC)<0)return -1;
    char userstr[16];snprintf(userstr,sizeof(userstr),"%d",user);
    pid_t child=fork();
    if(child==0) {
        dup2(pipefd[1],STDOUT_FILENO);close(pipefd[0]);close(pipefd[1]);
        char *argv[]={"/system/bin/settings","--user",userstr,(char*)op,"system",(char*)key,(char*)value,NULL};
        if(value==NULL)argv[6]=NULL;
        execv(argv[0],argv);_exit(127);
    }
    close(pipefd[1]);if(child<0){close(pipefd[0]);return -1;}
    size_t size=0;char chunk[128];ssize_t n;
    while((n=read(pipefd[0],chunk,sizeof(chunk)))>0) {
        if(output && size<capacity-1){size_t take=(size_t)n;if(take>capacity-1-size)take=capacity-1-size;memcpy(output+size,chunk,take);size+=take;}
    }
    close(pipefd[0]);if(output){output[size]=0;output[strcspn(output,"\r\n")]=0;}
    int status;while(waitpid(child,&status,0)<0){if(errno!=EINTR)return -1;}
    return WIFEXITED(status)&&WEXITSTATUS(status)==0?0:-1;
}

static void journal_path(int user,char *path,size_t length){snprintf(path,length,"/data/local/tmp/dev.spenmouse.restore.u%d",user);}
static int restore(int user,char originals[COUNT][16],int legacy) {
    int failed=0;
    for(size_t n=legacy?0:1;n<COUNT;n++) {
        if(strcmp(originals[n],"1")!=0)continue;
        char current[16]={0};
        if(command(user,"get",keys[n],NULL,current,sizeof(current))<0){failed=1;continue;}
        if(strcmp(current,"0")==0 && command(user,"put",keys[n],"1",NULL,0)<0)failed=1;
    }
    if(!failed){char path[128];journal_path(user,path,sizeof(path));unlink(path);}
    return failed?-1:0;
}
static int recover(int user) {
    char path[128];journal_path(user,path,sizeof(path));
    int fd=open(path,O_RDONLY|O_CLOEXEC|O_NOFOLLOW);
    if(fd<0)return errno==ENOENT?0:-1;
    FILE *file=fdopen(fd,"r");if(!file){close(fd);return -1;}
    char originals[COUNT][16]={{0}};int valid=1;
    for(size_t n=0;n<COUNT;n++) {
        if(!fgets(originals[n],sizeof(originals[n]),file)){valid=0;break;}
        originals[n][strcspn(originals[n],"\r\n")]=0;
        if(strcmp(originals[n],"0") && strcmp(originals[n],"1") && strcmp(originals[n],"null")){valid=0;break;}
    }
    fclose(file);return valid?restore(user,originals,1):-1;
}
static int suppress(int user,char originals[COUNT][16]) {
    char path[128];journal_path(user,path,sizeof(path));
    int fd=open(path,O_WRONLY|O_CREAT|O_TRUNC|O_CLOEXEC|O_NOFOLLOW,0600);
    if(fd<0)return -1;
    FILE *file=fdopen(fd,"w");if(!file){close(fd);return -1;}
    for(size_t n=0;n<COUNT;n++)fprintf(file,"%s\n",n==0?"null":originals[n]);
    int result=fflush(file);if(result==0)result=fsync(fd);fclose(file);if(result<0)return -1;
    for(size_t n=1;n<COUNT;n++) {
        if(strcmp(originals[n],"1")==0 && command(user,"put",keys[n],"0",NULL,0)<0)return -1;
    }
    return 0;
}
int main(int argc,char **argv) {
    if(argc<2)return 2;
    int user=atoi(argv[1]);if(user<0 || user>10000)return 2;
    prctl(PR_SET_NAME,"spen_guard",0,0,0);
    signal(SIGTERM,interrupt_handler);signal(SIGINT,interrupt_handler);signal(SIGHUP,interrupt_handler);
    if(recover(user)<0){send(3,"E",1,MSG_NOSIGNAL);return 3;}
    char originals[COUNT][16]={{0}};
    for(size_t n=0;n<COUNT;n++) {
        if(command(user,"get",keys[n],NULL,originals[n],sizeof(originals[n]))<0){send(3,"E",1,MSG_NOSIGNAL);return 4;}
        if(strcmp(originals[n],"0") && strcmp(originals[n],"1") && strcmp(originals[n],"null"))strcpy(originals[n],"null");
    }
    if(send(3,"O",1,MSG_NOSIGNAL)!=1)return 5;
    int active=0;
    while(!interrupted) {
        struct pollfd fd={.fd=3,.events=POLLIN};int ready=poll(&fd,1,1000);
        if(ready<0){if(errno==EINTR)continue;break;}
        if(!ready)continue;
        char cmd;if(recv(3,&cmd,1,0)!=1)break;
        int rc=0;
        if(cmd=='A' && !active){active=1;rc=suppress(user,originals);}
        else if((cmd=='R'||cmd=='X') && active){rc=restore(user,originals,0);if(rc==0)active=0;}
        if(send(3,rc==0?"O":"E",1,MSG_NOSIGNAL)!=1)break;
        if(cmd=='X')break;
    }
    // Socket EOF also covers a client crash or Shizuku being stopped.
    if(active)restore(user,originals,0);
    close(3);return 0;
}
